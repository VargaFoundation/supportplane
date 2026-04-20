package varga.supportplane.ai;

import varga.supportplane.ai.algorithm.ZScoreDetector;
import varga.supportplane.ai.model.*;
import varga.supportplane.dto.response.AIAnalysisResponse;
import varga.supportplane.infra.ClickHouseClient;
import varga.supportplane.model.Cluster;
import varga.supportplane.model.MetricHistory;
import varga.supportplane.repository.ClusterRepository;
import varga.supportplane.repository.MetricHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Orchestrator — coordinates all 4 AI engines.
 *
 * Data sources (priority order):
 * 1. ClickHouse (if enabled) — fast analytical queries on decomposed time-series
 * 2. MetricHistory (PostgreSQL fallback) — JSONB snapshots from bundle parsing
 *
 * This dual-mode allows the system to work without ClickHouse infrastructure
 * while benefiting from it when available.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIOrchestrator {

    private final ClusterRepository clusterRepository;
    private final MetricHistoryRepository metricHistoryRepository;
    private final ClickHouseClient clickHouse;
    private final AnomalyDetectionService anomalyService;
    private final PredictiveAnalysisService predictiveService;
    private final LogAnalysisService logAnalysisService;
    private final AutoTuningService autoTuningService;

    /**
     * Run full AI analysis on a cluster.
     * Uses ClickHouse for metrics if available, falls back to PostgreSQL metric_history.
     */
    public AIAnalysisResponse analyzeCluster(Long clusterId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));

        Map<String, Object> currentMetadata = cluster.getMetadata() != null
                ? cluster.getMetadata() : Collections.emptyMap();

        List<AnomalyResult> anomalies;
        List<PredictionResult> predictions;

        if (clickHouse.isEnabled()) {
            // Fast path: use ClickHouse analytical queries
            log.info("Running AI analysis on cluster {} via ClickHouse", cluster.getClusterId());
            anomalies = detectAnomaliesViaClickHouse(clusterId);
            predictions = predictViaClickHouse(clusterId);
        } else {
            // Fallback: use PostgreSQL metric_history JSONB
            List<MetricHistory> history = metricHistoryRepository
                    .findTop200ByClusterIdOrderByCollectedAtDesc(clusterId);
            List<Map<String, Object>> snapshots = history.stream()
                    .map(MetricHistory::getSnapshotData).collect(Collectors.toList());
            Collections.reverse(snapshots);
            log.info("Running AI analysis on cluster {} via metric_history ({} snapshots)",
                    cluster.getClusterId(), snapshots.size());
            anomalies = anomalyService.detectAnomalies(currentMetadata, snapshots);
            predictions = predictiveService.runPredictions(currentMetadata, snapshots);
        }

        List<LogCluster> logPatterns = logAnalysisService.analyzeLogs(currentMetadata);
        List<TuningRecommendation> tuning = autoTuningService.generateRecommendations(currentMetadata);

        return AIAnalysisResponse.builder()
                .clusterName(cluster.getName() != null ? cluster.getName() : cluster.getClusterId())
                .clusterId(cluster.getClusterId())
                .analyzedAt(LocalDateTime.now())
                .anomalies(anomalies)
                .predictions(predictions)
                .logPatterns(logPatterns)
                .tuningRecommendations(tuning)
                .summary(AIAnalysisResponse.Summary.builder()
                        .totalAnomalies(anomalies.size())
                        .criticalAnomalies((int) anomalies.stream()
                                .filter(a -> "CRITICAL".equals(a.getSeverity())).count())
                        .totalPredictions(predictions.size())
                        .urgentPredictions((int) predictions.stream()
                                .filter(p -> "CRITICAL".equals(p.getSeverity())).count())
                        .logPatternCount(logPatterns.size())
                        .tuningRecommendationCount(tuning.size())
                        .build())
                .build();
    }

    /**
     * Anomaly detection via ClickHouse: query pre-computed stats (mean, stddev, p95)
     * and apply Z-score detection on the latest value vs historical distribution.
     */
    private List<AnomalyResult> detectAnomaliesViaClickHouse(Long clusterId) {
        Map<String, Map<String, Double>> stats = clickHouse.queryMetricStats(clusterId, 7);
        List<AnomalyResult> anomalies = new ArrayList<>();

        for (Map.Entry<String, Map<String, Double>> entry : stats.entrySet()) {
            String metricName = entry.getKey();
            Map<String, Double> s = entry.getValue();
            double mean = s.getOrDefault("mean", 0.0);
            double stddev = s.getOrDefault("stddev", 0.0);
            double latest = s.getOrDefault("latest", 0.0);

            if (stddev < 1e-10) continue;

            double zScore = Math.abs((latest - mean) / stddev);
            if (zScore > 3.0) {
                String severity = zScore > 6.0 ? "CRITICAL" : zScore > 4.5 ? "WARNING" : "INFO";
                anomalies.add(AnomalyResult.builder()
                        .metricName(metricName)
                        .currentValue(latest)
                        .expectedValue(mean)
                        .deviation(zScore)
                        .anomalyScore(Math.min(1.0, zScore / 6.0))
                        .severity(severity)
                        .method("CLICKHOUSE_ZSCORE")
                        .description(String.format(
                                "%.2f deviates %.1fσ from 7-day mean %.2f (stddev=%.2f, p95=%.2f, n=%.0f)",
                                latest, zScore, mean, stddev,
                                s.getOrDefault("p95", 0.0), s.getOrDefault("sample_count", 0.0)))
                        .build());
            }
        }

        anomalies.sort(Comparator.comparingDouble(AnomalyResult::getAnomalyScore).reversed());
        return anomalies;
    }

    /**
     * Predictions via ClickHouse: query time series for capacity metrics
     * and run linear regression for exhaustion forecasting.
     */
    private List<PredictionResult> predictViaClickHouse(Long clusterId) {
        List<PredictionResult> predictions = new ArrayList<>();

        // Metrics to forecast
        Map<String, Double> thresholds = Map.of(
                "memory_percent", 90.0,
                "bench_mem_used_pct", 90.0,
                "bench_swap_used_pct", 5.0
        );

        for (Map.Entry<String, Double> entry : thresholds.entrySet()) {
            List<double[]> ts = clickHouse.queryTimeSeries(clusterId, entry.getKey(), 30);
            if (ts.size() >= 5) {
                List<TimeSeriesPoint> series = ts.stream()
                        .map(p -> new TimeSeriesPoint((long) p[0], p[1], entry.getKey()))
                        .collect(Collectors.toList());

                varga.supportplane.ai.algorithm.LinearRegression lr =
                        new varga.supportplane.ai.algorithm.LinearRegression();
                predictions.add(lr.predictExhaustion(series, entry.getValue(), entry.getKey()));
            }
        }

        predictions.sort((a, b) -> {
            int sevA = "CRITICAL".equals(a.getSeverity()) ? 3 : "WARNING".equals(a.getSeverity()) ? 2 : 1;
            int sevB = "CRITICAL".equals(b.getSeverity()) ? 3 : "WARNING".equals(b.getSeverity()) ? 2 : 1;
            return sevB - sevA;
        });
        return predictions;
    }
}
