package com.odp.supportplane.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "recommendation_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecommendationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 500)
    private String title;

    private String description;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 100)
    private String subcategory;

    @Column(nullable = false, length = 100)
    private String component;

    private String threat;

    private String vulnerability;

    private String asset;

    private String impact;

    @Column(name = "default_likelihood")
    @Builder.Default
    private String defaultLikelihood = "MEDIUM";

    @Column(name = "default_severity")
    @Builder.Default
    private String defaultSeverity = "WARNING";

    @Column(name = "recommendations_text")
    private String recommendationsText;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> condition;

    @Builder.Default
    private Boolean enabled = true;

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
