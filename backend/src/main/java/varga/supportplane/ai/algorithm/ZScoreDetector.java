package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.AnomalyResult;
import varga.supportplane.ai.model.TimeSeriesPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptive Z-Score anomaly detector with sliding window.
 *
 * Detects anomalies by computing the z-score of each point relative to
 * a sliding window of historical data. Handles seasonality through
 * a simplified STL decomposition (subtract rolling mean of period).
 *
 * Research basis: standard statistical process control, extended with
 * adaptive windowing for non-stationary time series typical of
 * distributed system metrics.
 */
public class ZScoreDetector {

    private final double threshold;
    private final int windowSize;

    public ZScoreDetector(double threshold, int windowSize) {
        this.threshold = threshold;
        this.windowSize = windowSize;
    }

    public ZScoreDetector() {
        this(3.0, 100); // Default: 3-sigma, 100-point window
    }

    /**
     * Detect anomalies in a time series using adaptive z-score.
     *
     * @param series the time series points (ordered by timestamp)
     * @return list of detected anomalies with scores
     */
    public List<AnomalyResult> detect(List<TimeSeriesPoint> series) {
        List<AnomalyResult> anomalies = new ArrayList<>();
        if (series == null || series.size() < windowSize + 1) {
            return anomalies;
        }

        // Deseasonalize if enough data (remove rolling mean of period)
        double[] values = series.stream().mapToDouble(TimeSeriesPoint::getValue).toArray();
        double[] deseasonalized = deseasonalize(values);

        // Sliding window z-score
        for (int i = windowSize; i < deseasonalized.length; i++) {
            double[] window = new double[windowSize];
            System.arraycopy(deseasonalized, i - windowSize, window, 0, windowSize);

            double mean = mean(window);
            double std = stddev(window, mean);

            if (std < 1e-10) continue; // Skip constant windows

            double zScore = Math.abs((deseasonalized[i] - mean) / std);

            if (zScore > threshold) {
                String severity = zScore > threshold * 2 ? "CRITICAL" :
                                  zScore > threshold * 1.5 ? "WARNING" : "INFO";

                anomalies.add(AnomalyResult.builder()
                        .metricName(series.get(i).getMetricName())
                        .currentValue(series.get(i).getValue())
                        .expectedValue(mean)
                        .deviation(zScore)
                        .anomalyScore(Math.min(1.0, zScore / (threshold * 2)))
                        .severity(severity)
                        .method("ZSCORE")
                        .description(String.format(
                                "Value %.2f deviates %.1f sigma from mean %.2f (window=%d)",
                                series.get(i).getValue(), zScore, mean, windowSize))
                        .build());
            }
        }
        return anomalies;
    }

    /**
     * CUSUM (Cumulative Sum) change detection.
     * Detects sustained shifts in the mean level of a time series.
     *
     * @param series the time series
     * @param driftAllowance slack to ignore small shifts (typically 0.5 * sigma)
     * @return list of detected change points
     */
    public List<AnomalyResult> detectChanges(List<TimeSeriesPoint> series, double driftAllowance) {
        List<AnomalyResult> changes = new ArrayList<>();
        if (series == null || series.size() < 20) return changes;

        double[] values = series.stream().mapToDouble(TimeSeriesPoint::getValue).toArray();
        double mean = mean(values);
        double std = stddev(values, mean);

        if (std < 1e-10) return changes;

        double drift = driftAllowance * std;
        double cusumHigh = 0;
        double cusumLow = 0;
        double detectThreshold = 5 * std;

        for (int i = 0; i < values.length; i++) {
            cusumHigh = Math.max(0, cusumHigh + values[i] - mean - drift);
            cusumLow = Math.max(0, cusumLow - values[i] + mean - drift);

            if (cusumHigh > detectThreshold || cusumLow > detectThreshold) {
                String direction = cusumHigh > detectThreshold ? "upward" : "downward";
                changes.add(AnomalyResult.builder()
                        .metricName(series.get(i).getMetricName())
                        .currentValue(values[i])
                        .expectedValue(mean)
                        .deviation(Math.max(cusumHigh, cusumLow) / std)
                        .anomalyScore(Math.min(1.0, Math.max(cusumHigh, cusumLow) / (detectThreshold * 2)))
                        .severity("WARNING")
                        .method("CUSUM")
                        .description(String.format(
                                "Sustained %s shift detected: current=%.2f, baseline=%.2f",
                                direction, values[i], mean))
                        .build());

                // Reset after detection
                cusumHigh = 0;
                cusumLow = 0;
            }
        }
        return changes;
    }

    /**
     * Simple deseasonalization by subtracting a rolling mean.
     */
    private double[] deseasonalize(double[] values) {
        int period = Math.min(24, values.length / 4); // Assume daily seasonality at most
        if (period < 3) return values.clone();

        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            int start = Math.max(0, i - period / 2);
            int end = Math.min(values.length, i + period / 2 + 1);
            double rollingMean = 0;
            for (int j = start; j < end; j++) rollingMean += values[j];
            rollingMean /= (end - start);
            result[i] = values[i] - rollingMean;
        }
        return result;
    }

    static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    static double stddev(double[] values, double mean) {
        double sumSq = 0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        return Math.sqrt(sumSq / values.length);
    }
}
