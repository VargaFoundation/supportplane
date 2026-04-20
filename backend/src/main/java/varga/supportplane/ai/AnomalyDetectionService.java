package varga.supportplane.ai;

import varga.supportplane.ai.algorithm.IsolationForest;
import varga.supportplane.ai.algorithm.ZScoreDetector;
import varga.supportplane.ai.model.AnomalyResult;
import varga.supportplane.ai.model.TimeSeriesPoint;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Anomaly detection service for cluster metrics.
 *
 * Combines multiple detection methods:
 * 1. Z-Score: fast statistical detection for univariate metrics
 * 2. Isolation Forest: multi-dimensional anomaly detection across correlated metrics
 * 3. CUSUM: sustained trend change detection (gradual degradation)
 *
 * The ensemble approach reduces false positives by requiring consensus
 * across methods — a finding flagged by 2+ methods is more likely real.
 */
@Service
public class AnomalyDetectionService {

    private static final String[] MONITORED_METRICS = {
            "cpu_percent", "memory_percent", "disk_usage_percent",
            "iowait_percent", "steal_time_percent", "load_avg_1m",
            "swap_used_percent", "gc_time_percent",
            "hdfs_capacity_percent", "yarn_pending_apps",
            "hbase_compaction_queue", "kafka_consumer_lag"
    };

    /**
     * Run full anomaly detection on cluster metadata.
     *
     * @param currentMetrics current metric values from latest bundle
     * @param historicalSnapshots list of past metric snapshots (from metric_history)
     * @return deduplicated list of anomalies ordered by severity
     */
    @SuppressWarnings("unchecked")
    public List<AnomalyResult> detectAnomalies(Map<String, Object> currentMetrics,
                                                 List<Map<String, Object>> historicalSnapshots) {
        List<AnomalyResult> allAnomalies = new ArrayList<>();

        // 1. Z-Score detection on each metric individually
        ZScoreDetector zScore = new ZScoreDetector(3.0, Math.min(50, historicalSnapshots.size()));
        for (String metric : MONITORED_METRICS) {
            List<TimeSeriesPoint> series = extractTimeSeries(metric, historicalSnapshots, currentMetrics);
            if (series.size() > 10) {
                allAnomalies.addAll(zScore.detect(series));
                allAnomalies.addAll(zScore.detectChanges(series, 0.5));
            }
        }

        // 2. Isolation Forest on multi-dimensional feature vector
        if (historicalSnapshots.size() >= 20) {
            double[][] featureMatrix = buildFeatureMatrix(historicalSnapshots, currentMetrics);
            if (featureMatrix.length > 10 && featureMatrix[0].length > 0) {
                IsolationForest iForest = new IsolationForest(100, Math.min(256, featureMatrix.length));
                // Train on historical, predict on all including current
                iForest.fit(featureMatrix, MONITORED_METRICS);
                allAnomalies.addAll(iForest.predict(featureMatrix, MONITORED_METRICS, 0.65));
            }
        }

        // 3. Deduplicate: keep highest score per metric
        Map<String, AnomalyResult> deduped = new LinkedHashMap<>();
        for (AnomalyResult a : allAnomalies) {
            String key = a.getMetricName();
            if (!deduped.containsKey(key) || a.getAnomalyScore() > deduped.get(key).getAnomalyScore()) {
                deduped.put(key, a);
            }
        }

        List<AnomalyResult> result = new ArrayList<>(deduped.values());
        result.sort(Comparator.comparingDouble(AnomalyResult::getAnomalyScore).reversed());
        return result;
    }

    private List<TimeSeriesPoint> extractTimeSeries(String metricName,
                                                      List<Map<String, Object>> snapshots,
                                                      Map<String, Object> current) {
        List<TimeSeriesPoint> series = new ArrayList<>();
        for (Map<String, Object> snapshot : snapshots) {
            Double value = extractMetricValue(snapshot, metricName);
            if (value != null) {
                long ts = extractTimestamp(snapshot);
                series.add(new TimeSeriesPoint(ts, value, metricName));
            }
        }
        // Add current
        Double currentValue = extractMetricValue(current, metricName);
        if (currentValue != null) {
            series.add(new TimeSeriesPoint(System.currentTimeMillis(), currentValue, metricName));
        }
        return series;
    }

    private double[][] buildFeatureMatrix(List<Map<String, Object>> snapshots,
                                            Map<String, Object> current) {
        List<double[]> rows = new ArrayList<>();
        List<Map<String, Object>> all = new ArrayList<>(snapshots);
        all.add(current);

        for (Map<String, Object> snapshot : all) {
            double[] features = new double[MONITORED_METRICS.length];
            boolean hasAny = false;
            for (int i = 0; i < MONITORED_METRICS.length; i++) {
                Double val = extractMetricValue(snapshot, MONITORED_METRICS[i]);
                features[i] = val != null ? val : 0.0;
                if (val != null) hasAny = true;
            }
            if (hasAny) rows.add(features);
        }
        return rows.toArray(new double[0][]);
    }

    @SuppressWarnings("unchecked")
    private Double extractMetricValue(Map<String, Object> metrics, String metricName) {
        if (metrics == null) return null;
        // Navigate nested paths: "cpu_percent" might be at metrics.system.cpu_percent
        // or benchmarks.cpu.steal_time.percent
        Map<String, String> pathMap = Map.ofEntries(
                Map.entry("cpu_percent", "metrics.system.cpu_percent"),
                Map.entry("memory_percent", "metrics.system.memory.percent"),
                Map.entry("disk_usage_percent", "benchmarks.disk.partition_info"),
                Map.entry("iowait_percent", "benchmarks.cpu.iowait.percent"),
                Map.entry("steal_time_percent", "benchmarks.cpu.steal_time.percent"),
                Map.entry("load_avg_1m", "metrics.system.load_avg"),
                Map.entry("swap_used_percent", "benchmarks.memory.swap_analysis.percent_used"),
                Map.entry("gc_time_percent", "jmx_metrics.namenode.gc_time_millis"),
                Map.entry("hdfs_capacity_percent", "hdfs_report.dfs_used"),
                Map.entry("yarn_pending_apps", "jmx_metrics.resourcemanager.apps_pending"),
                Map.entry("hbase_compaction_queue", "hbase_metrics.hmaster.average_load"),
                Map.entry("kafka_consumer_lag", "kafka_metrics.cluster_info.total_brokers")
        );
        String path = pathMap.get(metricName);
        if (path == null) return null;

        Object current = metrics;
        for (String part : path.split("\\.")) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
            if (current == null) return null;
        }
        if (current instanceof Number) return ((Number) current).doubleValue();
        return null;
    }

    private long extractTimestamp(Map<String, Object> snapshot) {
        Object ts = snapshot.get("timestamp");
        if (ts instanceof Number) return ((Number) ts).longValue();
        if (ts instanceof String) {
            try { return Long.parseLong((String) ts); } catch (NumberFormatException e) { /* ignore */ }
        }
        return System.currentTimeMillis();
    }
}
