package varga.supportplane.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Loki client for structured log ingestion via Push API.
 *
 * Sends log lines with labels (cluster_id, hostname, service, level)
 * for efficient querying via LogQL.
 */
@Component
@Slf4j
public class LokiClient {

    @Value("${loki.url:http://localhost:3100}")
    private String lokiUrl;

    @Value("${loki.enabled:false}")
    private boolean enabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Push log lines to Loki with labels.
     *
     * @param clusterId cluster identifier
     * @param hostname  source hostname
     * @param service   service name (HDFS, YARN, HBase, etc.)
     * @param level     log level (ERROR, WARN, INFO)
     * @param lines     log lines to push
     */
    public void pushLogs(String clusterId, String hostname, String service,
                          String level, List<String> lines) {
        if (!enabled || lines.isEmpty()) return;

        try {
            Map<String, String> labels = new LinkedHashMap<>();
            labels.put("cluster_id", clusterId);
            labels.put("hostname", hostname);
            labels.put("service", service);
            labels.put("level", level);

            // Build Loki push format
            List<List<String>> values = new ArrayList<>();
            long nanos = Instant.now().toEpochMilli() * 1_000_000L;
            for (String line : lines) {
                values.add(List.of(String.valueOf(nanos), line));
                nanos++; // Increment to ensure unique timestamps
            }

            Map<String, Object> stream = Map.of(
                    "stream", labels,
                    "values", values
            );
            Map<String, Object> payload = Map.of("streams", List.of(stream));

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(lokiUrl + "/loki/api/v1/push"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204 && response.statusCode() != 200) {
                log.warn("Loki push returned {}: {}", response.statusCode(), response.body());
            } else {
                log.debug("Pushed {} log lines to Loki [{}/{}@{}]",
                        lines.size(), service, level, hostname);
            }
        } catch (Exception e) {
            log.warn("Failed to push logs to Loki: {}", e.getMessage());
        }
    }

    /**
     * Push a batch of logs from multiple services/levels.
     */
    public void pushLogBatch(String clusterId, String hostname,
                               Map<String, Map<String, List<String>>> logsByServiceAndLevel) {
        if (!enabled) return;

        for (Map.Entry<String, Map<String, List<String>>> serviceEntry : logsByServiceAndLevel.entrySet()) {
            String service = serviceEntry.getKey();
            for (Map.Entry<String, List<String>> levelEntry : serviceEntry.getValue().entrySet()) {
                pushLogs(clusterId, hostname, service, levelEntry.getKey(), levelEntry.getValue());
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
