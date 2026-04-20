package varga.supportplane.dto.response;

import varga.supportplane.model.Recommendation;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RecommendationResponse {
    private Long id;
    private Long clusterId;
    private String clusterName;
    private String tenantName;
    private String title;
    private String description;
    private String severity;
    private String source;
    private String status;
    private String category;
    private String subcategory;
    private String component;
    private String findingStatus;
    private String threat;
    private String vulnerability;
    private String asset;
    private String impact;
    private String likelihood;
    private String risk;
    private String recommendationsText;
    private String ruleCode;
    private String createdByName;
    private LocalDateTime createdAt;

    public static RecommendationResponse from(Recommendation rec) {
        RecommendationResponse r = new RecommendationResponse();
        r.setId(rec.getId());
        if (rec.getCluster() != null) {
            r.setClusterId(rec.getCluster().getId());
            r.setClusterName(rec.getCluster().getName());
            if (rec.getCluster().getTenant() != null) {
                r.setTenantName(rec.getCluster().getTenant().getName());
            }
        }
        r.setTitle(rec.getTitle());
        r.setDescription(rec.getDescription());
        r.setSeverity(rec.getSeverity());
        r.setSource(rec.getSource());
        r.setStatus(rec.getStatus());
        r.setCategory(rec.getCategory());
        r.setSubcategory(rec.getSubcategory());
        r.setComponent(rec.getComponent());
        r.setFindingStatus(rec.getFindingStatus());
        r.setThreat(rec.getThreat());
        r.setVulnerability(rec.getVulnerability());
        r.setAsset(rec.getAsset());
        r.setImpact(rec.getImpact());
        r.setLikelihood(rec.getLikelihood());
        r.setRisk(rec.getRisk());
        r.setRecommendationsText(rec.getRecommendationsText());
        if (rec.getRule() != null) {
            r.setRuleCode(rec.getRule().getCode());
        }
        if (rec.getCreatedBy() != null) {
            r.setCreatedByName(rec.getCreatedBy().getFullName());
        }
        r.setCreatedAt(rec.getCreatedAt());
        return r;
    }
}
