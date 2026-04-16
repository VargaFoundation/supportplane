package com.odp.supportplane.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "recommendations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id")
    private Cluster cluster;

    @Column(nullable = false)
    private String title;

    private String description;

    @Builder.Default
    private String severity = "INFO";

    @Builder.Default
    private String source = "OPERATOR";

    @Builder.Default
    private String status = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private RecommendationRule rule;

    private String category;

    private String subcategory;

    private String component;

    private String threat;

    private String vulnerability;

    private String asset;

    private String impact;

    private String likelihood;

    private String risk;

    @Column(name = "recommendations_text")
    private String recommendationsText;

    @Column(name = "finding_status")
    @Builder.Default
    private String findingStatus = "UNKNOWN";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluation_data")
    private Map<String, Object> evaluationData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
