package com.odp.supportplane.dto.response;

import com.odp.supportplane.model.RecommendationRule;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class RecommendationRuleResponse {

    private Long id;
    private String code;
    private String title;
    private String description;
    private String category;
    private String subcategory;
    private String component;
    private String threat;
    private String vulnerability;
    private String asset;
    private String impact;
    private String defaultLikelihood;
    private String defaultSeverity;
    private String recommendationsText;
    private Map<String, Object> condition;
    private Boolean enabled;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RecommendationRuleResponse from(RecommendationRule rule) {
        RecommendationRuleResponse r = new RecommendationRuleResponse();
        r.setId(rule.getId());
        r.setCode(rule.getCode());
        r.setTitle(rule.getTitle());
        r.setDescription(rule.getDescription());
        r.setCategory(rule.getCategory());
        r.setSubcategory(rule.getSubcategory());
        r.setComponent(rule.getComponent());
        r.setThreat(rule.getThreat());
        r.setVulnerability(rule.getVulnerability());
        r.setAsset(rule.getAsset());
        r.setImpact(rule.getImpact());
        r.setDefaultLikelihood(rule.getDefaultLikelihood());
        r.setDefaultSeverity(rule.getDefaultSeverity());
        r.setRecommendationsText(rule.getRecommendationsText());
        r.setCondition(rule.getCondition());
        r.setEnabled(rule.getEnabled());
        if (rule.getCreatedBy() != null) {
            r.setCreatedByName(rule.getCreatedBy().getFullName());
        }
        r.setCreatedAt(rule.getCreatedAt());
        r.setUpdatedAt(rule.getUpdatedAt());
        return r;
    }
}
