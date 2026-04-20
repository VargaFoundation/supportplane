package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.AnomalyResult;
import varga.supportplane.ai.model.TimeSeriesPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZScoreDetectorTest {

    @Test
    void detect_normalData_noAnomalies() {
        ZScoreDetector detector = new ZScoreDetector(3.0, 20);
        List<TimeSeriesPoint> series = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            series.add(new TimeSeriesPoint(i * 1000L, 50 + Math.sin(i) * 2, "cpu"));
        }
        List<AnomalyResult> anomalies = detector.detect(series);
        assertTrue(anomalies.isEmpty(), "Normal sinusoidal data should not trigger anomalies");
    }

    @Test
    void detect_spikeData_detectsAnomaly() {
        ZScoreDetector detector = new ZScoreDetector(3.0, 20);
        List<TimeSeriesPoint> series = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double value = (i == 25) ? 200 : 50; // Spike at position 25
            series.add(new TimeSeriesPoint(i * 1000L, value, "cpu"));
        }
        List<AnomalyResult> anomalies = detector.detect(series);
        assertFalse(anomalies.isEmpty(), "Spike should be detected as anomaly");
        assertEquals("ZSCORE", anomalies.get(0).getMethod());
    }

    @Test
    void detectChanges_sustainedShift_detected() {
        ZScoreDetector detector = new ZScoreDetector(3.0, 20);
        List<TimeSeriesPoint> series = new ArrayList<>();
        // First half: mean=50, second half: mean=80
        for (int i = 0; i < 50; i++) {
            double value = i < 25 ? 50 : 80;
            series.add(new TimeSeriesPoint(i * 1000L, value, "memory"));
        }
        List<AnomalyResult> changes = detector.detectChanges(series, 0.5);
        assertFalse(changes.isEmpty(), "Sustained shift should be detected by CUSUM");
        assertEquals("CUSUM", changes.get(0).getMethod());
    }

    @Test
    void detect_tooFewPoints_returnsEmpty() {
        ZScoreDetector detector = new ZScoreDetector(3.0, 20);
        List<TimeSeriesPoint> series = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            series.add(new TimeSeriesPoint(i * 1000L, 50, "cpu"));
        }
        assertTrue(detector.detect(series).isEmpty());
    }
}
