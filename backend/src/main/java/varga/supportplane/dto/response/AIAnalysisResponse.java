package varga.supportplane.dto.response;

import varga.supportplane.ai.model.AnomalyResult;
import varga.supportplane.ai.model.LogCluster;
import varga.supportplane.ai.model.PredictionResult;
import varga.supportplane.ai.model.TuningRecommendation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIAnalysisResponse {
    private String clusterName;
    private String clusterId;
    private LocalDateTime analyzedAt;
    private String workloadProfile;
    private List<AnomalyResult> anomalies;
    private List<PredictionResult> predictions;
    private List<LogCluster> logPatterns;
    private List<TuningRecommendation> tuningRecommendations;
    private Summary summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private int totalAnomalies;
        private int criticalAnomalies;
        private int totalPredictions;
        private int urgentPredictions;
        private int logPatternCount;
        private int tuningRecommendationCount;
    }
}
