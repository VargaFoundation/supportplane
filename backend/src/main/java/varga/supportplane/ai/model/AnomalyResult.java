package varga.supportplane.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyResult {
    private String metricName;
    private double currentValue;
    private double expectedValue;
    private double deviation;
    private double anomalyScore;
    private String severity;       // INFO, WARNING, CRITICAL
    private String method;         // ZSCORE, ISOLATION_FOREST, CUSUM
    private String description;
}
