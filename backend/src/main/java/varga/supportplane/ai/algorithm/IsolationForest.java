package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.AnomalyResult;

import java.util.*;

/**
 * Isolation Forest — unsupervised anomaly detection algorithm.
 *
 * Based on: Liu, Ting & Zhou (2008) "Isolation Forest"
 * Key insight: anomalies are few and different, so they are isolated
 * in fewer random partitions than normal points.
 *
 * Implementation: from-scratch in Java, no ML library dependency.
 * Adapted for cluster metric vectors (multi-dimensional feature space
 * where each feature is a cluster metric like CPU, memory, I/O, etc.).
 */
public class IsolationForest {

    private final int numTrees;
    private final int subsampleSize;
    private final Random random;
    private List<IsolationTree> forest;
    private int dataSize;

    public IsolationForest(int numTrees, int subsampleSize) {
        this.numTrees = numTrees;
        this.subsampleSize = subsampleSize;
        this.random = new Random(42);
    }

    public IsolationForest() {
        this(100, 256);
    }

    /**
     * Fit the forest on training data.
     *
     * @param data matrix where each row is a sample, each column is a feature
     * @param featureNames names of features for result reporting
     */
    public void fit(double[][] data, String[] featureNames) {
        this.dataSize = data.length;
        int maxDepth = (int) Math.ceil(Math.log(subsampleSize) / Math.log(2));
        this.forest = new ArrayList<>();

        for (int i = 0; i < numTrees; i++) {
            double[][] subsample = subsample(data, Math.min(subsampleSize, data.length));
            forest.add(buildTree(subsample, 0, maxDepth));
        }
    }

    /**
     * Compute anomaly scores for all samples.
     * Score close to 1.0 = anomaly, close to 0.5 = normal, close to 0.0 = very normal.
     *
     * @param data the data to score
     * @param featureNames names for reporting
     * @param threshold score threshold above which a point is anomalous (default 0.6)
     * @return list of anomaly results for points exceeding threshold
     */
    public List<AnomalyResult> predict(double[][] data, String[] featureNames, double threshold) {
        List<AnomalyResult> anomalies = new ArrayList<>();
        double[] scores = scoreAll(data);

        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > threshold) {
                // Find which feature contributes most to the anomaly
                String topFeature = findTopFeature(data[i], featureNames);
                String severity = scores[i] > 0.8 ? "CRITICAL" :
                                  scores[i] > 0.7 ? "WARNING" : "INFO";

                anomalies.add(AnomalyResult.builder()
                        .metricName(topFeature)
                        .currentValue(data[i][findFeatureIndex(featureNames, topFeature)])
                        .anomalyScore(scores[i])
                        .severity(severity)
                        .method("ISOLATION_FOREST")
                        .description(String.format(
                                "Isolation Forest anomaly score=%.3f (threshold=%.2f), top feature: %s",
                                scores[i], threshold, topFeature))
                        .build());
            }
        }
        return anomalies;
    }

    /**
     * Compute anomaly scores for all data points.
     */
    public double[] scoreAll(double[][] data) {
        double[] scores = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            double avgPathLength = 0;
            for (IsolationTree tree : forest) {
                avgPathLength += pathLength(data[i], tree, 0);
            }
            avgPathLength /= forest.size();
            scores[i] = Math.pow(2, -avgPathLength / avgPathLength(dataSize));
        }
        return scores;
    }

    // --- Tree construction ---

    private IsolationTree buildTree(double[][] data, int depth, int maxDepth) {
        if (depth >= maxDepth || data.length <= 1) {
            return new IsolationTree(data.length);
        }

        int numFeatures = data[0].length;
        int splitFeature = random.nextInt(numFeatures);

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double[] row : data) {
            if (row[splitFeature] < min) min = row[splitFeature];
            if (row[splitFeature] > max) max = row[splitFeature];
        }

        if (Math.abs(max - min) < 1e-10) {
            return new IsolationTree(data.length);
        }

        double splitValue = min + random.nextDouble() * (max - min);

        List<double[]> leftData = new ArrayList<>();
        List<double[]> rightData = new ArrayList<>();
        for (double[] row : data) {
            if (row[splitFeature] < splitValue) {
                leftData.add(row);
            } else {
                rightData.add(row);
            }
        }

        if (leftData.isEmpty() || rightData.isEmpty()) {
            return new IsolationTree(data.length);
        }

        IsolationTree node = new IsolationTree(splitFeature, splitValue);
        node.left = buildTree(leftData.toArray(new double[0][]), depth + 1, maxDepth);
        node.right = buildTree(rightData.toArray(new double[0][]), depth + 1, maxDepth);
        return node;
    }

    private double pathLength(double[] point, IsolationTree node, int currentDepth) {
        if (node.isLeaf()) {
            return currentDepth + avgPathLength(node.size);
        }
        if (point[node.splitFeature] < node.splitValue) {
            return pathLength(point, node.left, currentDepth + 1);
        } else {
            return pathLength(point, node.right, currentDepth + 1);
        }
    }

    /**
     * Average path length of unsuccessful search in BST (harmonic number approximation).
     * c(n) = 2 * H(n-1) - 2(n-1)/n, where H(i) ≈ ln(i) + 0.5772156649
     */
    static double avgPathLength(int n) {
        if (n <= 1) return 0;
        if (n == 2) return 1;
        double harmonic = Math.log(n - 1) + 0.5772156649;
        return 2.0 * harmonic - 2.0 * (n - 1.0) / n;
    }

    private double[][] subsample(double[][] data, int size) {
        List<double[]> list = new ArrayList<>(Arrays.asList(data));
        Collections.shuffle(list, random);
        return list.subList(0, Math.min(size, list.size())).toArray(new double[0][]);
    }

    private String findTopFeature(double[] point, String[] featureNames) {
        // Simple: return the feature with the highest absolute z-score-like deviation
        if (featureNames == null || featureNames.length == 0) return "unknown";
        int maxIdx = 0;
        double maxVal = Math.abs(point[0]);
        for (int i = 1; i < point.length && i < featureNames.length; i++) {
            if (Math.abs(point[i]) > maxVal) {
                maxVal = Math.abs(point[i]);
                maxIdx = i;
            }
        }
        return featureNames[maxIdx];
    }

    private int findFeatureIndex(String[] featureNames, String name) {
        for (int i = 0; i < featureNames.length; i++) {
            if (featureNames[i].equals(name)) return i;
        }
        return 0;
    }

    // --- Tree node ---

    private static class IsolationTree {
        int splitFeature;
        double splitValue;
        int size;
        IsolationTree left, right;

        IsolationTree(int size) { // Leaf
            this.size = size;
            this.splitFeature = -1;
        }

        IsolationTree(int splitFeature, double splitValue) { // Internal
            this.splitFeature = splitFeature;
            this.splitValue = splitValue;
        }

        boolean isLeaf() {
            return splitFeature == -1;
        }
    }
}
