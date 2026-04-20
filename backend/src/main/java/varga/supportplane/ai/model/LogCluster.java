package varga.supportplane.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogCluster {
    private int clusterId;
    private String template;          // e.g., "ERROR: Block <*> is missing <*> replicas"
    private int occurrenceCount;
    private double severityScore;     // 0.0 - 1.0
    private String severity;          // INFO, WARNING, CRITICAL
    private String service;           // HDFS, YARN, HBase, etc.
    private List<String> sampleMessages;
    private List<String> correlatedClusters; // IDs of temporally correlated clusters
    private String suggestedAction;
}
