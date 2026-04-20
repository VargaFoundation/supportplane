package varga.supportplane.ai;

import varga.supportplane.ai.algorithm.ExponentialSmoothing;
import varga.supportplane.ai.algorithm.LinearRegression;
import varga.supportplane.ai.model.PredictionResult;
import varga.supportplane.ai.model.TimeSeriesPoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Predictive analysis service for capacity forecasting and failure prediction.
 *
 * Combines:
 * 1. Linear regression for long-term capacity trends
 * 2. Exponential smoothing for short-term forecasting
 * 3. SMART-based disk failure prediction
 */
@Service
public class PredictiveAnalysisService {

    /**
     * Run all predictions on cluster data.
     *
     * @param currentMetrics current metric snapshot
     * @param historicalSnapshots past metric snapshots
     * @return list of predictions ordered by urgency
     */
    public List<PredictionResult> runPredictions(Map<String, Object> currentMetrics,
                                                   List<Map<String, Object>> historicalSnapshots) {
        List<PredictionResult> predictions = new ArrayList<>();

        // 1. HDFS capacity exhaustion prediction
        List<TimeSeriesPoint> hdfsUsage = extractCapacitySeries(historicalSnapshots, currentMetrics, "hdfs");
        if (hdfsUsage.size() >= 5) {
            LinearRegression lr = new LinearRegression();
            predictions.add(lr.predictExhaustion(hdfsUsage, 90.0, "HDFS Capacity (%)"));
        }

        // 2. Disk usage per partition
        List<TimeSeriesPoint> diskUsage = extractCapacitySeries(historicalSnapshots, currentMetrics, "disk_root");
        if (diskUsage.size() >= 5) {
            LinearRegression lr = new LinearRegression();
            predictions.add(lr.predictExhaustion(diskUsage, 85.0, "Root Disk Usage (%)"));
        }

        // 3. NameNode heap exhaustion
        List<TimeSeriesPoint> nnHeap = extractCapacitySeries(historicalSnapshots, currentMetrics, "nn_heap");
        if (nnHeap.size() >= 5) {
            LinearRegression lr = new LinearRegression();
            predictions.add(lr.predictExhaustion(nnHeap, 80.0, "NameNode Heap Usage (%)"));
        }

        // 4. Memory usage trend
        List<TimeSeriesPoint> memUsage = extractCapacitySeries(historicalSnapshots, currentMetrics, "memory");
        if (memUsage.size() >= 5) {
            LinearRegression lr = new LinearRegression();
            predictions.add(lr.predictExhaustion(memUsage, 90.0, "System Memory Usage (%)"));
        }

        // 5. SMART disk failure prediction
        predictions.addAll(predictDiskFailures(currentMetrics));

        // 6. Short-term forecasts using Exponential Smoothing
        if (hdfsUsage.size() >= 10) {
            ExponentialSmoothing es = new ExponentialSmoothing(0.3, 0.1);
            double[] forecast = es.forecast(hdfsUsage, 7);
            if (forecast.length > 0 && forecast[forecast.length - 1] > 85) {
                predictions.add(PredictionResult.builder()
                        .metricName("HDFS Capacity 7-day Forecast")
                        .predictionType("TREND")
                        .currentValue(hdfsUsage.get(hdfsUsage.size() - 1).getValue())
                        .predictedValue(forecast[forecast.length - 1])
                        .daysUntilThreshold(7)
                        .confidence(0.7)
                        .severity(forecast[forecast.length - 1] > 90 ? "CRITICAL" : "WARNING")
                        .description(String.format("HDFS usage predicted to reach %.1f%% in 7 days", forecast[6]))
                        .build());
            }
        }

        // Remove INFO-only predictions, sort by severity
        predictions.sort((a, b) -> {
            int sevA = "CRITICAL".equals(a.getSeverity()) ? 3 : "WARNING".equals(a.getSeverity()) ? 2 : 1;
            int sevB = "CRITICAL".equals(b.getSeverity()) ? 3 : "WARNING".equals(b.getSeverity()) ? 2 : 1;
            return sevB - sevA;
        });

        return predictions;
    }

    @SuppressWarnings("unchecked")
    private List<PredictionResult> predictDiskFailures(Map<String, Object> metrics) {
        List<PredictionResult> results = new ArrayList<>();
        Object infraDiag = metrics.get("infra_diagnostics");
        if (!(infraDiag instanceof Map)) return results;

        Object smartList = ((Map<String, Object>) infraDiag).get("disk_smart");
        if (!(smartList instanceof List)) return results;

        for (Object item : (List<?>) smartList) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> disk = (Map<String, Object>) item;
            String diskName = (String) disk.getOrDefault("disk", "unknown");
            String health = (String) disk.getOrDefault("health", "unknown");
            int reallocated = ((Number) disk.getOrDefault("reallocated_sectors", 0)).intValue();
            int pending = ((Number) disk.getOrDefault("pending_sectors", 0)).intValue();

            // Failure risk score: based on Backblaze research
            double riskScore = 0;
            if ("FAILED".equals(health)) riskScore = 1.0;
            else if (reallocated > 0) riskScore += Math.min(0.5, reallocated * 0.05);
            if (pending > 0) riskScore += Math.min(0.5, pending * 0.1);
            riskScore = Math.min(1.0, riskScore);

            if (riskScore > 0.1) {
                String severity = riskScore > 0.7 ? "CRITICAL" : riskScore > 0.3 ? "WARNING" : "INFO";
                results.add(PredictionResult.builder()
                        .metricName("Disk " + diskName + " Failure Risk")
                        .predictionType("DISK_FAILURE")
                        .currentValue(riskScore * 100)
                        .threshold(70)
                        .confidence(0.6 + riskScore * 0.3)
                        .severity(severity)
                        .description(String.format(
                                "Disk %s: health=%s, reallocated=%d, pending=%d → risk score=%.0f%%",
                                diskName, health, reallocated, pending, riskScore * 100))
                        .build());
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private List<TimeSeriesPoint> extractCapacitySeries(List<Map<String, Object>> snapshots,
                                                          Map<String, Object> current, String type) {
        List<TimeSeriesPoint> series = new ArrayList<>();
        List<Map<String, Object>> all = new ArrayList<>(snapshots);
        all.add(current);

        for (Map<String, Object> snapshot : all) {
            Double value = null;
            long ts = System.currentTimeMillis();

            switch (type) {
                case "hdfs":
                    value = navigateDouble(snapshot, "hdfs_report", "dfs_used");
                    if (value != null) {
                        Double total = navigateDouble(snapshot, "hdfs_report", "configured_capacity");
                        if (total != null && total > 0) value = (value / total) * 100;
                    }
                    break;
                case "disk_root":
                    value = navigateDouble(snapshot, "metrics", "system", "disk_usage", "/", "percent");
                    break;
                case "nn_heap":
                    Double used = navigateDouble(snapshot, "jmx_metrics", "namenode", "heap_used_mb");
                    Double max = navigateDouble(snapshot, "jmx_metrics", "namenode", "heap_max_mb");
                    if (used != null && max != null && max > 0) value = (used / max) * 100;
                    break;
                case "memory":
                    value = navigateDouble(snapshot, "metrics", "system", "memory", "percent");
                    break;
            }

            if (value != null) {
                Object tsObj = snapshot.get("timestamp");
                if (tsObj instanceof Number) ts = ((Number) tsObj).longValue();
                series.add(new TimeSeriesPoint(ts, value, type));
            }
        }
        return series;
    }

    @SuppressWarnings("unchecked")
    private Double navigateDouble(Map<String, Object> map, String... path) {
        Object current = map;
        for (String key : path) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }
        if (current instanceof Number) return ((Number) current).doubleValue();
        return null;
    }
}
