package com.odp.supportplane.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "bundles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id")
    private Cluster cluster;

    @Column(name = "bundle_id", unique = true)
    private String bundleId;

    @Column(nullable = false)
    private String filename;

    private String filepath;

    @Column(name = "size_bytes")
    @Builder.Default
    private Long sizeBytes = 0L;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "analysis_summary")
    private String analysisSummary;

    @Column(name = "received_at")
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();
}
