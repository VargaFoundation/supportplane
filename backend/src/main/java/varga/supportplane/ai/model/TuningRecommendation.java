package varga.supportplane.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TuningRecommendation {
    private String parameter;         // e.g., "hbase.regionserver.handler.count"
    private String component;         // HBase, HDFS, YARN, etc.
    private String currentValue;
    private String suggestedValue;
    private String justification;
    private String workloadProfile;   // CPU_BOUND, IO_BOUND, MEMORY_BOUND, MIXED
    private double confidence;        // 0.0 - 1.0
    private String expectedImpact;    // e.g., "+15% read throughput"
}
