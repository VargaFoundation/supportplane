package varga.supportplane.ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPoint {
    private long timestamp;
    private double value;
    private String metricName;
}
