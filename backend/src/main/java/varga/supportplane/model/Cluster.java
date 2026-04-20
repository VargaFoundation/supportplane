package varga.supportplane.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "clusters")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cluster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "cluster_id", nullable = false)
    private String clusterId;

    private String name;

    @Builder.Default
    private String status = "PENDING";

    @Column(name = "otp_validated")
    @Builder.Default
    private Boolean otpValidated = false;

    @Column(name = "last_bundle_at")
    private LocalDateTime lastBundleAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "source_ip")
    private String sourceIp;

    @Column(name = "geo_location")
    private String geoLocation;

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
