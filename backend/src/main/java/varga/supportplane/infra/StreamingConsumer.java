package varga.supportplane.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Continuous ingestion consumer.
 *
 * Subscribes to Pulsar topics {@code metrics} and {@code logs} in the
 * configured default namespace. Each message is a JSON envelope containing
 * the tenant context and a payload that is routed to ClickHouse or Loki.
 *
 * Envelope format for metrics:
 * <pre>
 * {
 *   "tenantId":   "acme",
 *   "clusterId":  42,
 *   "nodeId":     "worker-17",
 *   "timestamp":  "2026-04-20T10:12:03.123Z",
 *   "kind":       "host" | "jmx",
 *   "service":    "HDFS",          // jmx only
 *   "component":  "NameNode",      // jmx only
 *   "metrics":    {"cpu_percent": 42.1, ...}
 * }
 * </pre>
 *
 * Envelope format for logs:
 * <pre>
 * {
 *   "tenantId":  "acme",
 *   "clusterId": "cl-acme-001",    // external id for Loki labels
 *   "nodeId":    "worker-17",
 *   "service":   "HDFS",
 *   "level":     "ERROR",
 *   "lines":     ["2026-04-20 ...", ...]
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreamingConsumer {

    private final PulsarClient pulsar;
    private final ClickHouseClient clickHouse;
    private final LokiClient loki;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${pulsar.consumer.metrics-subscription:supportplane-metrics-ingest}")
    private String metricsSubscription;

    @Value("${pulsar.consumer.logs-subscription:supportplane-logs-ingest}")
    private String logsSubscription;

    @Value("${pulsar.consumer.tenant-namespace:default}")
    private String consumerNamespace;

    private Consumer<byte[]> metricsConsumer;
    private Consumer<byte[]> logsConsumer;

    @PostConstruct
    public void start() {
        if (!pulsar.isEnabled()) {
            log.info("StreamingConsumer idle — Pulsar disabled");
            return;
        }
        try {
            metricsConsumer = pulsar.subscribe(consumerNamespace, "metrics",
                    metricsSubscription, metricsListener());
            logsConsumer = pulsar.subscribe(consumerNamespace, "logs",
                    logsSubscription, logsListener());
            log.info("StreamingConsumer subscribed: metrics={} logs={}",
                    metricsConsumer.getTopic(), logsConsumer.getTopic());
        } catch (Exception e) {
            log.warn("Failed to start StreamingConsumer: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        closeQuietly(metricsConsumer);
        closeQuietly(logsConsumer);
    }

    private MessageListener<byte[]> metricsListener() {
        return (consumer, msg) -> {
            try {
                handleMetric(msg);
                consumer.acknowledgeAsync(msg);
            } catch (Exception e) {
                log.warn("Metric ingest failed, nacking: {}", e.getMessage());
                consumer.negativeAcknowledge(msg);
            }
        };
    }

    private MessageListener<byte[]> logsListener() {
        return (consumer, msg) -> {
            try {
                handleLog(msg);
                consumer.acknowledgeAsync(msg);
            } catch (Exception e) {
                log.warn("Log ingest failed, nacking: {}", e.getMessage());
                consumer.negativeAcknowledge(msg);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void handleMetric(Message<byte[]> msg) throws Exception {
        Map<String, Object> env = mapper.readValue(msg.getValue(), Map.class);
        Long clusterId = toLong(env.get("clusterId"));
        String nodeId = str(env.get("nodeId"), "unknown");
        String kind = str(env.get("kind"), "host");
        Instant ts = parseInstant(env.get("timestamp"));
        Object rawMetrics = env.get("metrics");
        if (!(rawMetrics instanceof Map) || clusterId == null) return;

        Map<String, Double> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : ((Map<String, Object>) rawMetrics).entrySet()) {
            if (e.getValue() instanceof Number n) {
                metrics.put(e.getKey(), n.doubleValue());
            }
        }
        if (metrics.isEmpty()) return;

        if ("jmx".equalsIgnoreCase(kind)) {
            String service = str(env.get("service"), "Unknown");
            String component = str(env.get("component"), service);
            clickHouse.insertJmxMetrics(clusterId, nodeId, service, component, metrics, ts);
        } else {
            clickHouse.insertMetrics(clusterId, nodeId, metrics, ts);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleLog(Message<byte[]> msg) throws Exception {
        Map<String, Object> env = mapper.readValue(msg.getValue(), Map.class);
        String clusterId = str(env.get("clusterId"), "unknown");
        String nodeId = str(env.get("nodeId"), "unknown");
        String service = str(env.get("service"), "Other");
        String level = str(env.get("level"), "INFO");
        Object linesObj = env.get("lines");
        if (!(linesObj instanceof List<?> rawLines)) return;

        List<String> lines = new ArrayList<>(rawLines.size());
        for (Object o : rawLines) {
            if (o != null) lines.add(o.toString());
        }
        if (lines.isEmpty()) return;
        loki.pushLogs(clusterId, nodeId, service, level, lines);
    }

    private static String str(Object o, String fallback) {
        return o == null ? fallback : o.toString();
    }

    private static Long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static Instant parseInstant(Object o) {
        if (o instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        if (o instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return Instant.now();
    }

    private void closeQuietly(Consumer<?> c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) {}
    }
}
