package varga.supportplane.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "metric_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MetricHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_data", nullable = false)
    private Map<String, Object> snapshotData;

    @Column(name = "collected_at")
    @Builder.Default
    private LocalDateTime collectedAt = LocalDateTime.now();
}
