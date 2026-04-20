package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.PredictionResult;
import varga.supportplane.ai.model.TimeSeriesPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinearRegressionTest {

    @Test
    void fit_perfectLine_rSquaredIsOne() {
        LinearRegression lr = new LinearRegression();
        List<TimeSeriesPoint> series = new ArrayList<>();
        long day = 86400000L;
        for (int i = 0; i < 10; i++) {
            series.add(new TimeSeriesPoint(i * day, 10 + i * 5, "disk_usage"));
        }
        lr.fit(series);
        assertEquals(1.0, lr.getRSquared(), 0.001, "Perfect line should have R²=1");
        assertEquals(5.0, lr.getSlope(), 0.001, "Slope should be 5 per day");
    }

    @Test
    void predictExhaustion_growingMetric_predictsFutureDate() {
        LinearRegression lr = new LinearRegression();
        List<TimeSeriesPoint> series = new ArrayList<>();
        long day = 86400000L;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            // 50% growing by 1% per day → reaches 90% in 40 days
            series.add(new TimeSeriesPoint(now - (30 - i) * day, 50 + i, "hdfs"));
        }

        PredictionResult result = lr.predictExhaustion(series, 90, "HDFS Usage");
        assertEquals("CAPACITY_EXHAUSTION", result.getPredictionType());
        assertTrue(result.getDaysUntilThreshold() > 0, "Should predict future exhaustion");
        assertTrue(result.getDaysUntilThreshold() < 60, "Should be within reasonable range");
        assertNotNull(result.getExhaustionDate());
    }

    @Test
    void predictExhaustion_flatMetric_noExhaustion() {
        LinearRegression lr = new LinearRegression();
        List<TimeSeriesPoint> series = new ArrayList<>();
        long day = 86400000L;
        for (int i = 0; i < 30; i++) {
            series.add(new TimeSeriesPoint(i * day, 50, "disk"));
        }

        PredictionResult result = lr.predictExhaustion(series, 90, "Disk");
        assertEquals(-1, result.getDaysUntilThreshold(), "Flat data should not predict exhaustion");
    }

    @Test
    void predictExhaustion_decreasingMetric_noExhaustion() {
        LinearRegression lr = new LinearRegression();
        List<TimeSeriesPoint> series = new ArrayList<>();
        long day = 86400000L;
        for (int i = 0; i < 30; i++) {
            series.add(new TimeSeriesPoint(i * day, 80 - i, "disk"));
        }

        PredictionResult result = lr.predictExhaustion(series, 90, "Disk");
        assertEquals(-1, result.getDaysUntilThreshold());
    }
}
