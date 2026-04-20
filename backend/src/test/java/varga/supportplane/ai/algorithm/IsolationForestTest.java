package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.AnomalyResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class IsolationForestTest {

    @Test
    void scoreAll_normalData_lowScores() {
        IsolationForest forest = new IsolationForest(50, 100);
        Random rng = new Random(42);

        // Normal cluster around (50, 50)
        double[][] data = new double[200][2];
        for (int i = 0; i < 200; i++) {
            data[i][0] = 50 + rng.nextGaussian() * 5;
            data[i][1] = 50 + rng.nextGaussian() * 5;
        }

        forest.fit(data, new String[]{"cpu", "memory"});
        double[] scores = forest.scoreAll(data);

        double avgScore = 0;
        for (double s : scores) avgScore += s;
        avgScore /= scores.length;

        assertTrue(avgScore < 0.6, "Average score for normal data should be below 0.6, got " + avgScore);
    }

    @Test
    void predict_withOutlier_detected() {
        IsolationForest forest = new IsolationForest(100, 100);
        Random rng = new Random(42);

        // Normal cluster + 1 outlier
        double[][] data = new double[101][2];
        for (int i = 0; i < 100; i++) {
            data[i][0] = 50 + rng.nextGaussian() * 3;
            data[i][1] = 50 + rng.nextGaussian() * 3;
        }
        // Outlier far from the cluster
        data[100][0] = 200;
        data[100][1] = 200;

        forest.fit(data, new String[]{"cpu", "memory"});
        List<AnomalyResult> anomalies = forest.predict(data, new String[]{"cpu", "memory"}, 0.6);

        // The outlier should have one of the highest scores
        double[] scores = forest.scoreAll(data);
        double outlierScore = scores[100];
        assertTrue(outlierScore > 0.5, "Outlier should have high anomaly score, got " + outlierScore);
    }

    @Test
    void avgPathLength_knownValues() {
        assertEquals(0, IsolationForest.avgPathLength(1), 0.001);
        assertEquals(1, IsolationForest.avgPathLength(2), 0.001);
        assertTrue(IsolationForest.avgPathLength(256) > 5);
    }
}
