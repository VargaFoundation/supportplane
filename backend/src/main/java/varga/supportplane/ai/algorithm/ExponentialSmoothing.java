package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.TimeSeriesPoint;

import java.util.List;

/**
 * Holt-Winters Double Exponential Smoothing for trend-aware forecasting.
 *
 * Models both level and trend of a time series, producing short-term
 * forecasts (7-30 days) with trend extrapolation.
 *
 * Parameters:
 * - alpha (0-1): smoothing factor for level (higher = more reactive)
 * - beta (0-1): smoothing factor for trend (higher = more reactive)
 */
public class ExponentialSmoothing {

    private final double alpha;
    private final double beta;

    public ExponentialSmoothing(double alpha, double beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    public ExponentialSmoothing() {
        this(0.3, 0.1); // Moderate smoothing defaults
    }

    /**
     * Forecast future values using double exponential smoothing.
     *
     * @param series historical time series
     * @param horizon number of periods to forecast
     * @return array of forecasted values
     */
    public double[] forecast(List<TimeSeriesPoint> series, int horizon) {
        if (series == null || series.size() < 2) {
            return new double[horizon];
        }

        double[] values = series.stream().mapToDouble(TimeSeriesPoint::getValue).toArray();
        int n = values.length;

        // Initialize
        double level = values[0];
        double trend = values[1] - values[0];

        double[] smoothed = new double[n];
        smoothed[0] = level;

        // Smooth the series
        for (int i = 1; i < n; i++) {
            double prevLevel = level;
            level = alpha * values[i] + (1 - alpha) * (prevLevel + trend);
            trend = beta * (level - prevLevel) + (1 - beta) * trend;
            smoothed[i] = level;
        }

        // Forecast
        double[] result = new double[horizon];
        for (int h = 0; h < horizon; h++) {
            result[h] = level + (h + 1) * trend;
        }
        return result;
    }

    /**
     * Compute forecast error metrics (MAE, RMSE) using leave-last-out validation.
     *
     * @param series the full time series
     * @param testSize number of last points to use as test set
     * @return array of [MAE, RMSE]
     */
    public double[] evaluateAccuracy(List<TimeSeriesPoint> series, int testSize) {
        if (series == null || series.size() < testSize + 10) {
            return new double[]{Double.MAX_VALUE, Double.MAX_VALUE};
        }

        List<TimeSeriesPoint> train = series.subList(0, series.size() - testSize);
        double[] forecasted = forecast(train, testSize);

        double sumAbsError = 0;
        double sumSqError = 0;
        for (int i = 0; i < testSize; i++) {
            double actual = series.get(series.size() - testSize + i).getValue();
            double error = actual - forecasted[i];
            sumAbsError += Math.abs(error);
            sumSqError += error * error;
        }

        double mae = sumAbsError / testSize;
        double rmse = Math.sqrt(sumSqError / testSize);
        return new double[]{mae, rmse};
    }
}
