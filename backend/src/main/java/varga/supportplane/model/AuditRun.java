package varga.supportplane.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_runs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by")
    private User triggeredBy;

    @Builder.Default
    private String status = "RUNNING";

    @Column(name = "rules_evaluated")
    @Builder.Default
    private Integer rulesEvaluated = 0;

    @Column(name = "findings_count")
    @Builder.Default
    private Integer findingsCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> summary;

    @Column(name = "started_at")
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
