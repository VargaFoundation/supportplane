package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.PredictionResult;
import varga.supportplane.ai.model.TimeSeriesPoint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Linear Regression for trend projection and capacity exhaustion prediction.
 *
 * Uses Ordinary Least Squares (OLS) to fit a line y = a + b*x to time series data,
 * then projects forward to predict when a threshold will be reached.
 *
 * Also computes R² (coefficient of determination) as a confidence measure —
 * higher R² means the linear model fits well and the projection is reliable.
 */
public class LinearRegression {

    private double slope;      // b: rate of change per unit time
    private double intercept;  // a: value at time 0
    private double rSquared;   // R²: goodness of fit (0-1)

    /**
     * Fit the linear model to time series data.
     *
     * @param series the time series (timestamp in epoch millis, value)
     */
    public void fit(List<TimeSeriesPoint> series) {
        if (series == null || series.size() < 2) {
            slope = 0;
            intercept = 0;
            rSquared = 0;
            return;
        }

        int n = series.size();
        // Normalize timestamps to days from first point for numerical stability
        long t0 = series.get(0).getTimestamp();
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = (series.get(i).getTimestamp() - t0) / 86400000.0; // millis to days
            y[i] = series.get(i).getValue();
        }

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }

        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-10) {
            slope = 0;
            intercept = sumY / n;
            rSquared = 0;
            return;
        }

        slope = (n * sumXY - sumX * sumY) / denom;
        intercept = (sumY - slope * sumX) / n;

        // R² calculation
        double meanY = sumY / n;
        double ssTotal = 0, ssResidual = 0;
        for (int i = 0; i < n; i++) {
            double predicted = intercept + slope * x[i];
            ssTotal += (y[i] - meanY) * (y[i] - meanY);
            ssResidual += (y[i] - predicted) * (y[i] - predicted);
        }
        rSquared = ssTotal > 0 ? 1.0 - ssResidual / ssTotal : 0;
    }

    /**
     * Predict when a threshold will be reached.
     *
     * @param series the time series used for fitting
     * @param threshold the threshold value (e.g., 90% disk usage)
     * @param metricName name for the result
     * @return prediction result with estimated date and confidence
     */
    public PredictionResult predictExhaustion(List<TimeSeriesPoint> series,
                                               double threshold, String metricName) {
        fit(series);

        if (series == null || series.isEmpty()) {
            return PredictionResult.builder()
                    .metricName(metricName)
                    .predictionType("CAPACITY_EXHAUSTION")
                    .confidence(0)
                    .severity("INFO")
                    .description("Insufficient data for prediction")
                    .build();
        }

        double currentValue = series.get(series.size() - 1).getValue();
        long currentTimestamp = series.get(series.size() - 1).getTimestamp();

        // If slope is zero or negative (decreasing), no exhaustion
        if (slope <= 0) {
            return PredictionResult.builder()
                    .metricName(metricName)
                    .predictionType("CAPACITY_EXHAUSTION")
                    .currentValue(currentValue)
                    .growthRatePerDay(slope)
                    .threshold(threshold)
                    .daysUntilThreshold(-1)
                    .confidence(rSquared)
                    .severity("INFO")
                    .description("No growth trend detected (slope=" + String.format("%.4f", slope) + "/day)")
                    .build();
        }

        // Project forward: days until threshold
        long t0 = series.get(0).getTimestamp();
        double currentDays = (currentTimestamp - t0) / 86400000.0;
        double currentProjected = intercept + slope * currentDays;
        double daysUntilThreshold = (threshold - currentProjected) / slope;

        if (daysUntilThreshold < 0) {
            // Already exceeded threshold
            return PredictionResult.builder()
                    .metricName(metricName)
                    .predictionType("CAPACITY_EXHAUSTION")
                    .currentValue(currentValue)
                    .predictedValue(currentProjected)
                    .growthRatePerDay(slope)
                    .threshold(threshold)
                    .daysUntilThreshold(0)
                    .confidence(rSquared)
                    .severity("CRITICAL")
                    .description("Threshold already exceeded")
                    .build();
        }

        LocalDate exhaustionDate = Instant.ofEpochMilli(currentTimestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays((long) daysUntilThreshold);

        String severity = daysUntilThreshold < 7 ? "CRITICAL" :
                          daysUntilThreshold < 30 ? "WARNING" : "INFO";

        return PredictionResult.builder()
                .metricName(metricName)
                .predictionType("CAPACITY_EXHAUSTION")
                .currentValue(currentValue)
                .predictedValue(threshold)
                .growthRatePerDay(slope)
                .threshold(threshold)
                .exhaustionDate(exhaustionDate)
                .daysUntilThreshold((int) daysUntilThreshold)
                .confidence(rSquared)
                .severity(severity)
                .description(String.format(
                        "At current growth rate (%.2f/day), threshold %.0f will be reached in %d days (R²=%.3f)",
                        slope, threshold, (int) daysUntilThreshold, rSquared))
                .build();
    }

    public double getSlope() { return slope; }
    public double getIntercept() { return intercept; }
    public double getRSquared() { return rSquared; }
}
