package com.odp.supportplane.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private String tenantId;

    @Builder.Default
    private Boolean active = true;

    @Column(name = "license_tier")
    @Builder.Default
    private String licenseTier = "BASIC";

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "support_level")
    private String supportLevel;

    @Column(name = "contract_reference")
    private String contractReference;

    @Column(name = "contract_framework")
    private String contractFramework;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    private String notes;

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
