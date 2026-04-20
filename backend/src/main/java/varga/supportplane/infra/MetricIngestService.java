package varga.supportplane.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Decomposes bundle metadata into structured metric points
 * and pushes them to ClickHouse (metrics) and Loki (logs).
 *
 * This is the bridge between the monolithic JSONB bundle format
 * and the structured time-series/log storage backends.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricIngestService {

    private final ClickHouseClient clickHouse;
    private final LokiClient loki;

    /**
     * Ingest all metrics from a parsed bundle into ClickHouse and Loki.
     *
     * @param clusterId  the cluster DB id
     * @param clusterExternalId  the external cluster identifier (for Loki labels)
     * @param metadata   the full extracted cluster metadata from bundle parsing
     */
    @SuppressWarnings("unchecked")
    public void ingest(long clusterId, String clusterExternalId, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return;

        Instant now = Instant.now();
        String nodeId = extractNodeId(metadata);
        int totalMetrics = 0;

        // 1. System metrics (CPU, memory, disk, swap, load)
        Map<String, Double> systemMetrics = extractSystemMetrics(metadata);
        if (!systemMetrics.isEmpty()) {
            clickHouse.insertMetrics(clusterId, nodeId, systemMetrics, now);
            totalMetrics += systemMetrics.size();
        }

        // 2. Benchmark metrics
        Map<String, Double> benchMetrics = extractBenchmarkMetrics(metadata);
        if (!benchMetrics.isEmpty()) {
            clickHouse.insertMetrics(clusterId, nodeId, benchMetrics, now);
            totalMetrics += benchMetrics.size();
        }

        // 3. JMX metrics per service
        totalMetrics += ingestJmxMetrics(clusterId, metadata, now);

        // 4. Kernel parameters as metrics
        Map<String, Double> kernelMetrics = extractKernelMetrics(metadata);
        if (!kernelMetrics.isEmpty()) {
            clickHouse.insertMetrics(clusterId, nodeId, kernelMetrics, now);
            totalMetrics += kernelMetrics.size();
        }

        // 5. Logs to Loki
        ingestLogs(clusterExternalId, nodeId, metadata);

        if (totalMetrics > 0) {
            log.info("Ingested {} metric points into ClickHouse for cluster={}, node={}",
                    totalMetrics, clusterId, nodeId);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractSystemMetrics(Map<String, Object> metadata) {
        Map<String, Double> metrics = new LinkedHashMap<>();

        // From metrics.system
        Object metricsObj = metadata.get("metrics");
        if (metricsObj instanceof Map) {
            Map<String, Object> metricsMap = (Map<String, Object>) metricsObj;
            Object system = metricsMap.get("system");
            if (system instanceof Map) {
                Map<String, Object> sys = (Map<String, Object>) system;
                putDouble(metrics, "cpu_percent", sys.get("cpu_percent"));
                putDouble(metrics, "cpu_count", sys.get("cpu_count"));

                Object mem = sys.get("memory");
                if (mem instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) mem;
                    putDouble(metrics, "memory_percent", m.get("percent"));
                    putDouble(metrics, "memory_available", m.get("available"));
                    putDouble(metrics, "memory_total", m.get("total"));
                    putDouble(metrics, "memory_used", m.get("used"));
                }

                Object swap = sys.get("swap");
                if (swap instanceof Map) {
                    putDouble(metrics, "swap_percent", ((Map<String, Object>) swap).get("percent"));
                    putDouble(metrics, "swap_used", ((Map<String, Object>) swap).get("used"));
                }

                Object loadAvg = sys.get("load_avg");
                if (loadAvg instanceof List && ((List<?>) loadAvg).size() >= 3) {
                    List<?> la = (List<?>) loadAvg;
                    putDouble(metrics, "load_avg_1m", la.get(0));
                    putDouble(metrics, "load_avg_5m", la.get(1));
                    putDouble(metrics, "load_avg_15m", la.get(2));
                }
            }
        }
        return metrics;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractBenchmarkMetrics(Map<String, Object> metadata) {
        Map<String, Double> metrics = new LinkedHashMap<>();

        Object bench = metadata.get("benchmarks");
        if (!(bench instanceof Map)) return metrics;
        Map<String, Object> b = (Map<String, Object>) bench;

        // CPU benchmarks
        Object cpu = b.get("cpu");
        if (cpu instanceof Map) {
            Map<String, Object> c = (Map<String, Object>) cpu;
            Object steal = c.get("steal_time");
            if (steal instanceof Map) putDouble(metrics, "bench_cpu_steal", ((Map<String, Object>) steal).get("percent"));
            Object iowait = c.get("iowait");
            if (iowait instanceof Map) putDouble(metrics, "bench_cpu_iowait", ((Map<String, Object>) iowait).get("percent"));
            Object freq = c.get("cpu_frequency");
            if (freq instanceof Map) putDouble(metrics, "bench_cpu_freq_ratio", ((Map<String, Object>) freq).get("scaling_ratio"));
        }

        // Memory benchmarks
        Object mem = b.get("memory");
        if (mem instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) mem;
            Object sysMem = m.get("system_memory");
            if (sysMem instanceof Map) putDouble(metrics, "bench_mem_used_pct", ((Map<String, Object>) sysMem).get("used_percent"));
            Object swapAn = m.get("swap_analysis");
            if (swapAn instanceof Map) putDouble(metrics, "bench_swap_used_pct", ((Map<String, Object>) swapAn).get("percent_used"));
        }

        // Disk benchmarks
        Object disk = b.get("disk");
        if (disk instanceof Map) {
            Map<String, Object> d = (Map<String, Object>) disk;
            Object seqW = d.get("sequential_write");
            if (seqW instanceof Map) putDouble(metrics, "bench_disk_write_mbps", ((Map<String, Object>) seqW).get("throughput_mb_per_second"));
            Object lat = d.get("disk_latency");
            if (lat instanceof Map) {
                putDouble(metrics, "bench_disk_latency_avg", ((Map<String, Object>) lat).get("avg_ms"));
                putDouble(metrics, "bench_disk_latency_p95", ((Map<String, Object>) lat).get("p95_ms"));
            }
        }

        return metrics;
    }

    @SuppressWarnings("unchecked")
    private int ingestJmxMetrics(long clusterId, Map<String, Object> metadata, Instant now) {
        int count = 0;

        // NameNode JMX
        Object jmx = metadata.get("jmx_metrics");
        if (jmx instanceof Map) {
            Map<String, Object> jmxMap = (Map<String, Object>) jmx;

            Object nn = jmxMap.get("namenode");
            if (nn instanceof Map) {
                Map<String, Object> nnMap = (Map<String, Object>) nn;
                Map<String, Double> nnMetrics = new LinkedHashMap<>();
                String nnHost = (String) nnMap.getOrDefault("host", "namenode");
                putDouble(nnMetrics, "heap_used_mb", nnMap.get("heap_used_mb"));
                putDouble(nnMetrics, "heap_max_mb", nnMap.get("heap_max_mb"));
                putDouble(nnMetrics, "blocks_total", nnMap.get("blocks_total"));
                putDouble(nnMetrics, "missing_blocks", nnMap.get("missing_blocks"));
                putDouble(nnMetrics, "under_replicated_blocks", nnMap.get("under_replicated_blocks"));
                putDouble(nnMetrics, "capacity_used", nnMap.get("capacity_used"));
                putDouble(nnMetrics, "capacity_total", nnMap.get("capacity_total"));
                putDouble(nnMetrics, "gc_count", nnMap.get("gc_count"));
                putDouble(nnMetrics, "gc_time_millis", nnMap.get("gc_time_millis"));
                clickHouse.insertJmxMetrics(clusterId, nnHost, "HDFS", "NameNode", nnMetrics, now);
                count += nnMetrics.size();
            }

            Object rm = jmxMap.get("resourcemanager");
            if (rm instanceof Map) {
                Map<String, Object> rmMap = (Map<String, Object>) rm;
                Map<String, Double> rmMetrics = new LinkedHashMap<>();
                String rmHost = (String) rmMap.getOrDefault("host", "resourcemanager");
                putDouble(rmMetrics, "apps_running", rmMap.get("apps_running"));
                putDouble(rmMetrics, "apps_pending", rmMap.get("apps_pending"));
                putDouble(rmMetrics, "allocated_mb", rmMap.get("allocated_mb"));
                putDouble(rmMetrics, "available_mb", rmMap.get("available_mb"));
                putDouble(rmMetrics, "num_active_nms", rmMap.get("num_active_nms"));
                clickHouse.insertJmxMetrics(clusterId, rmHost, "YARN", "ResourceManager", rmMetrics, now);
                count += rmMetrics.size();
            }
        }

        return count;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractKernelMetrics(Map<String, Object> metadata) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        Object kernel = metadata.get("kernel_params");
        if (!(kernel instanceof Map)) return metrics;
        Map<String, Object> k = (Map<String, Object>) kernel;

        Object sysctl = k.get("sysctl");
        if (sysctl instanceof Map) {
            Map<String, Object> s = (Map<String, Object>) sysctl;
            for (Map.Entry<String, Object> entry : s.entrySet()) {
                try {
                    metrics.put("kern_" + entry.getKey().replace('.', '_'),
                            Double.parseDouble(String.valueOf(entry.getValue())));
                } catch (NumberFormatException e) {
                    // Skip non-numeric sysctl values
                }
            }
        }

        Object fd = k.get("file_descriptors");
        if (fd instanceof Map) {
            putDouble(metrics, "kern_fd_allocated", ((Map<String, Object>) fd).get("allocated"));
            putDouble(metrics, "kern_fd_max", ((Map<String, Object>) fd).get("max"));
        }

        return metrics;
    }

    @SuppressWarnings("unchecked")
    private void ingestLogs(String clusterId, String nodeId, Map<String, Object> metadata) {
        Object logTails = metadata.get("log_tails");
        if (!(logTails instanceof Map)) return;

        Map<String, Object> logs = (Map<String, Object>) logTails;
        for (Map.Entry<String, Object> entry : logs.entrySet()) {
            if (!(entry.getValue() instanceof String)) continue;
            String filepath = entry.getKey();
            String content = (String) entry.getValue();

            String service = guessService(filepath);
            String[] lines = content.split("\n");

            // Separate by level
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (String line : lines) {
                if (line.contains("ERROR") || line.contains("FATAL") || line.contains("Exception")) {
                    errors.add(line);
                } else if (line.contains("WARN")) {
                    warnings.add(line);
                }
            }

            if (!errors.isEmpty()) {
                loki.pushLogs(clusterId, nodeId, service, "ERROR", errors);
            }
            if (!warnings.isEmpty()) {
                loki.pushLogs(clusterId, nodeId, service, "WARN", warnings);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String extractNodeId(Map<String, Object> metadata) {
        Object sysInfo = metadata.get("system_info");
        if (sysInfo instanceof Map) {
            Object hostname = ((Map<String, Object>) sysInfo).get("hostname");
            if (hostname instanceof String) return (String) hostname;
        }
        return "unknown";
    }

    private String guessService(String filepath) {
        String lower = filepath.toLowerCase();
        if (lower.contains("namenode") || lower.contains("hdfs")) return "HDFS";
        if (lower.contains("datanode")) return "HDFS";
        if (lower.contains("resourcemanager") || lower.contains("yarn")) return "YARN";
        if (lower.contains("nodemanager")) return "YARN";
        if (lower.contains("hbase") || lower.contains("regionserver")) return "HBase";
        if (lower.contains("hive")) return "Hive";
        if (lower.contains("kafka")) return "Kafka";
        if (lower.contains("zookeeper")) return "ZooKeeper";
        if (lower.contains("impala")) return "Impala";
        if (lower.contains("spark")) return "Spark";
        return "Other";
    }

    private void putDouble(Map<String, Double> map, String key, Object value) {
        if (value instanceof Number) {
            map.put(key, ((Number) value).doubleValue());
        }
    }
}
