package varga.supportplane.ai;

import varga.supportplane.ai.algorithm.BayesianOptimizer;
import varga.supportplane.ai.model.TuningRecommendation;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Auto-tuning service: analyzes workload profile and recommends
 * optimal configuration parameter values.
 *
 * Combines:
 * 1. Workload classification (CPU/IO/Memory/Mixed bound)
 * 2. Expert knowledge base (known-good ranges per parameter)
 * 3. Bayesian optimization for continuous parameters
 */
@Service
public class AutoTuningService {

    private final BayesianOptimizer optimizer = new BayesianOptimizer();

    /**
     * Generate tuning recommendations from cluster metrics.
     *
     * @param clusterMetadata full cluster metadata from latest bundle
     * @return list of tuning recommendations sorted by confidence
     */
    @SuppressWarnings("unchecked")
    public List<TuningRecommendation> generateRecommendations(Map<String, Object> clusterMetadata) {
        if (clusterMetadata == null || clusterMetadata.isEmpty()) {
            return Collections.emptyList();
        }

        // Extract workload indicators
        double cpuPercent = extractDouble(clusterMetadata, "metrics.system.cpu_percent", 50);
        double iowaitPercent = extractDouble(clusterMetadata, "benchmarks.cpu.iowait.percent", 5);
        double memoryPercent = extractDouble(clusterMetadata, "metrics.system.memory.percent", 50);
        long swapUsed = (long) extractDouble(clusterMetadata, "benchmarks.memory.swap_analysis.used_mb", 0) * 1024 * 1024;

        // Classify workload
        BayesianOptimizer.WorkloadProfile profile =
                optimizer.classifyWorkload(cpuPercent, iowaitPercent, memoryPercent, swapUsed);

        // Build metric map
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("memory.total_gb", extractDouble(clusterMetadata, "benchmarks.memory.system_memory.total_gb", 64));
        metrics.put("cpu.cores", extractDouble(clusterMetadata, "benchmarks.cpu.cpu_info.logical_cores", 8));
        metrics.put("jvm.heap_max_gb", extractDouble(clusterMetadata, "jmx_metrics.namenode.heap_max_mb", 8192) / 1024);

        // HBase-specific
        metrics.put("hbase.rs.heap_max_mb", extractDouble(clusterMetadata, "hbase_metrics.regionservers", 0));
        if (clusterMetadata.containsKey("hbase_metrics")) {
            Map<String, Object> hbase = (Map<String, Object>) clusterMetadata.get("hbase_metrics");
            List<?> rsList = (List<?>) hbase.get("regionservers");
            if (rsList != null && !rsList.isEmpty()) {
                Map<String, Object> firstRs = (Map<String, Object>) rsList.get(0);
                metrics.put("hbase.rs.region_count", ((Number) firstRs.getOrDefault("region_count", 0)).doubleValue());
                metrics.put("hbase.rs.heap_max_mb", ((Number) firstRs.getOrDefault("heap_max_mb", 0)).doubleValue());
            }
        }

        // Build current config map from collected data
        Map<String, String> currentConfig = extractCurrentConfig(clusterMetadata);

        // Generate recommendations
        List<TuningRecommendation> recommendations = optimizer.recommend(profile, metrics, currentConfig);

        // Sort by confidence descending
        recommendations.sort(Comparator.comparingDouble(TuningRecommendation::getConfidence).reversed());

        return recommendations;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractCurrentConfig(Map<String, Object> metadata) {
        Map<String, String> config = new HashMap<>();

        // YARN
        config.put("yarn.nodemanager.resource.memory-mb",
                String.valueOf((int) extractDouble(metadata, "yarn_queues.scheduler_type", 0)));

        // Hive
        if (metadata.containsKey("hive_metrics")) {
            Map<String, Object> hive = (Map<String, Object>) metadata.get("hive_metrics");
            Map<String, Object> hiveCfg = (Map<String, Object>) hive.getOrDefault("config", Map.of());
            config.put("hive.execution.engine", (String) hiveCfg.getOrDefault("execution_engine", "mr"));
            config.put("hive.tez.container.size", (String) hiveCfg.getOrDefault("tez_container_size", ""));
        }

        // Spark
        if (metadata.containsKey("spark_metrics")) {
            Map<String, Object> spark = (Map<String, Object>) metadata.get("spark_metrics");
            Map<String, Object> sparkCfg = (Map<String, Object>) spark.getOrDefault("config", Map.of());
            config.put("spark.sql.adaptive.enabled", (String) sparkCfg.getOrDefault("adaptive_enabled", "false"));
            config.put("spark.serializer", (String) sparkCfg.getOrDefault("serializer", ""));
            config.put("spark.dynamicAllocation.enabled", (String) sparkCfg.getOrDefault("dynamic_allocation", "false"));
        }

        // Kernel
        config.put("vm.swappiness",
                String.valueOf((int) extractDouble(metadata, "kernel_params.sysctl.vm.swappiness", 60)));

        return config;
    }

    @SuppressWarnings("unchecked")
    private double extractDouble(Map<String, Object> map, String path, double defaultValue) {
        Object current = map;
        for (String part : path.split("\\.")) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return defaultValue;
            }
            if (current == null) return defaultValue;
        }
        if (current instanceof Number) return ((Number) current).doubleValue();
        if (current instanceof String) {
            try { return Double.parseDouble((String) current); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
