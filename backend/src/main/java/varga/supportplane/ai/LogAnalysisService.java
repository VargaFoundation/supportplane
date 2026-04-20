package varga.supportplane.ai;

import varga.supportplane.ai.algorithm.LogTokenizer;
import varga.supportplane.ai.model.LogCluster;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Log analysis service using NLP-inspired template extraction and clustering.
 *
 * Processes raw log data from bundles to:
 * 1. Extract log templates (Drain algorithm variant)
 * 2. Cluster similar error patterns
 * 3. Detect temporal correlations between error types
 * 4. Score severity and suggest remediation actions
 */
@Service
public class LogAnalysisService {

    private static final Map<String, String> KNOWN_REMEDIATION = Map.ofEntries(
            Map.entry("OutOfMemoryError", "Increase JVM heap size or investigate memory leak"),
            Map.entry("GC overhead", "Tune GC parameters or reduce heap pressure"),
            Map.entry("Connection refused", "Verify target service is running and network is reachable"),
            Map.entry("KerberosAuthException", "Refresh keytab or check KDC connectivity"),
            Map.entry("BlockMissing", "Check DataNode health, run hdfs fsck to identify affected files"),
            Map.entry("RegionServer abort", "Check RS logs for OOM or ZK session timeout, restart RS"),
            Map.entry("ZooKeeper session expired", "Increase ZK session timeout or reduce GC pauses"),
            Map.entry("SafeMode", "Check for missing blocks: hdfs dfsadmin -safemode get"),
            Map.entry("DiskError", "Check SMART status, replace failing disk"),
            Map.entry("CorruptBlock", "Run hdfs fsck / -delete to remove corrupt blocks"),
            Map.entry("LeaseExpired", "Client process died while writing, file will auto-recover"),
            Map.entry("NotServingRegion", "Region may be in transition, check HBase Master for RIT"),
            Map.entry("ContainerKilled", "Container exceeded memory limit, increase YARN container size"),
            Map.entry("Preemption", "Queue capacity exceeded, review YARN queue allocation")
    );

    /**
     * Analyze log data from cluster bundle.
     *
     * @param logData raw log data from bundle (map of filename -> content, or analysis.json)
     * @return list of log clusters sorted by severity
     */
    @SuppressWarnings("unchecked")
    public List<LogCluster> analyzeLogs(Map<String, Object> logData) {
        if (logData == null || logData.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect all log lines from various sources
        List<String> allLines = new ArrayList<>();

        // From log_tails (map of filepath -> content)
        Object logTails = logData.get("log_tails");
        if (logTails instanceof Map) {
            for (Object content : ((Map<String, Object>) logTails).values()) {
                if (content instanceof String) {
                    Collections.addAll(allLines, ((String) content).split("\n"));
                }
            }
        }

        // From analysis.json (pre-analyzed log data from supportcollector)
        Object analysis = logData.get("analysis");
        if (analysis instanceof Map) {
            Map<String, Object> analysisMap = (Map<String, Object>) analysis;
            // Extract stack traces
            Object stackTraces = analysisMap.get("stack_traces");
            if (stackTraces instanceof List) {
                for (Object st : (List<?>) stackTraces) {
                    if (st instanceof String) allLines.add((String) st);
                }
            }
            // Extract error summary
            Object errorSummary = analysisMap.get("error_summary");
            if (errorSummary instanceof Map) {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) errorSummary).entrySet()) {
                    allLines.add("ERROR " + entry.getKey() + " count=" + entry.getValue());
                }
            }
        }

        // Filter to error/warning lines only (skip INFO/DEBUG for ML processing)
        List<String> significantLines = allLines.stream()
                .filter(line -> line != null && !line.isBlank())
                .filter(line -> containsSeverity(line))
                .limit(50000) // Cap for performance
                .collect(Collectors.toList());

        if (significantLines.isEmpty()) {
            return Collections.emptyList();
        }

        // Run template extraction and clustering
        LogTokenizer tokenizer = new LogTokenizer(0.5);
        List<LogCluster> clusters = tokenizer.parse(significantLines);

        // Enrich with remediation suggestions
        for (LogCluster cluster : clusters) {
            String template = cluster.getTemplate();
            for (Map.Entry<String, String> entry : KNOWN_REMEDIATION.entrySet()) {
                if (template.contains(entry.getKey())) {
                    cluster.setSuggestedAction(entry.getValue());
                    break;
                }
            }
        }

        // Detect temporal correlations
        detectCorrelations(clusters, significantLines);

        return clusters;
    }

    private boolean containsSeverity(String line) {
        return line.contains("ERROR") || line.contains("WARN") ||
               line.contains("FATAL") || line.contains("Exception") ||
               line.contains("CRITICAL") || line.contains("OutOfMemory") ||
               line.contains("Failed") || line.contains("abort");
    }

    /**
     * Detect which log clusters tend to co-occur (appear in same time window).
     * Simplified: uses position proximity in log as time proxy.
     */
    private void detectCorrelations(List<LogCluster> clusters, List<String> lines) {
        if (clusters.size() < 2) return;

        // Build position index: which cluster does each line belong to?
        Map<Integer, Integer> lineToCluster = new HashMap<>();
        for (LogCluster cluster : clusters) {
            for (String sample : cluster.getSampleMessages()) {
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains(sample.substring(0, Math.min(50, sample.length())))) {
                        lineToCluster.put(i, cluster.getClusterId());
                        break;
                    }
                }
            }
        }

        // Find co-occurring clusters within window of 10 lines
        int windowSize = 10;
        Map<String, Integer> coOccurrences = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            Integer clusterId = lineToCluster.get(i);
            if (clusterId == null) continue;
            for (int j = i + 1; j < Math.min(i + windowSize, lines.size()); j++) {
                Integer otherClusterId = lineToCluster.get(j);
                if (otherClusterId != null && !otherClusterId.equals(clusterId)) {
                    String key = Math.min(clusterId, otherClusterId) + "-" + Math.max(clusterId, otherClusterId);
                    coOccurrences.merge(key, 1, Integer::sum);
                }
            }
        }

        // Set correlations on clusters (those with co-occurrence > 3)
        for (Map.Entry<String, Integer> entry : coOccurrences.entrySet()) {
            if (entry.getValue() >= 3) {
                String[] ids = entry.getKey().split("-");
                for (LogCluster cluster : clusters) {
                    if (String.valueOf(cluster.getClusterId()).equals(ids[0])) {
                        List<String> corr = cluster.getCorrelatedClusters();
                        if (corr == null) corr = new ArrayList<>();
                        corr.add("cluster_" + ids[1]);
                        cluster.setCorrelatedClusters(corr);
                    }
                    if (String.valueOf(cluster.getClusterId()).equals(ids[1])) {
                        List<String> corr = cluster.getCorrelatedClusters();
                        if (corr == null) corr = new ArrayList<>();
                        corr.add("cluster_" + ids[0]);
                        cluster.setCorrelatedClusters(corr);
                    }
                }
            }
        }
    }
}
