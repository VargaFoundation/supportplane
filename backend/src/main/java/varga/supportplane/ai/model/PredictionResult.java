package varga.supportplane.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionResult {
    private String metricName;
    private String predictionType; // CAPACITY_EXHAUSTION, DISK_FAILURE, TREND
    private double currentValue;
    private double predictedValue;
    private double growthRatePerDay;
    private double threshold;
    private LocalDate exhaustionDate;
    private int daysUntilThreshold;
    private double confidence;     // 0.0 - 1.0
    private String severity;
    private String description;
}
