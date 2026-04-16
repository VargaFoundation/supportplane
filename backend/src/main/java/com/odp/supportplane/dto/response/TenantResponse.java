package com.odp.supportplane.dto.response;

import com.odp.supportplane.model.Tenant;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TenantResponse {
    private Long id;
    private String name;
    private String tenantId;
    private Boolean active;
    private String licenseTier;

    // Contract info
    private String clientName;
    private String supportLevel;
    private String contractReference;
    private String contractFramework;
    private LocalDate contractEndDate;
    private String notes;

    // Computed counters (set by service)
    private Long clusterCount;
    private Long userCount;
    private Long openTicketCount;

    private LocalDateTime createdAt;

    public static TenantResponse from(Tenant tenant) {
        TenantResponse r = new TenantResponse();
        r.setId(tenant.getId());
        r.setName(tenant.getName());
        r.setTenantId(tenant.getTenantId());
        r.setActive(tenant.getActive());
        r.setLicenseTier(tenant.getLicenseTier());
        r.setClientName(tenant.getClientName());
        r.setSupportLevel(tenant.getSupportLevel());
        r.setContractReference(tenant.getContractReference());
        r.setContractFramework(tenant.getContractFramework());
        r.setContractEndDate(tenant.getContractEndDate());
        r.setNotes(tenant.getNotes());
        r.setCreatedAt(tenant.getCreatedAt());
        return r;
    }

    public static TenantResponse from(Tenant tenant, long clusterCount, long userCount, long openTicketCount) {
        TenantResponse r = from(tenant);
        r.setClusterCount(clusterCount);
        r.setUserCount(userCount);
        r.setOpenTicketCount(openTicketCount);
        return r;
    }
}
