package com.odp.supportplane.dto.response;

import com.odp.supportplane.model.Recommendation;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RecommendationResponse {
    private Long id;
    private Long clusterId;
    private String title;
    private String description;
    private String severity;
    private String source;
    private String status;
    private String createdByName;
    private LocalDateTime createdAt;

    public static RecommendationResponse from(Recommendation rec) {
        RecommendationResponse r = new RecommendationResponse();
        r.setId(rec.getId());
        if (rec.getCluster() != null) {
            r.setClusterId(rec.getCluster().getId());
        }
        r.setTitle(rec.getTitle());
        r.setDescription(rec.getDescription());
        r.setSeverity(rec.getSeverity());
        r.setSource(rec.getSource());
        r.setStatus(rec.getStatus());
        if (rec.getCreatedBy() != null) {
            r.setCreatedByName(rec.getCreatedBy().getFullName());
        }
        r.setCreatedAt(rec.getCreatedAt());
        return r;
    }
}
