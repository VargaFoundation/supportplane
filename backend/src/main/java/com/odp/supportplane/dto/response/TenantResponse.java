package com.odp.supportplane.dto.response;

import com.odp.supportplane.model.Tenant;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TenantResponse {
    private Long id;
    private String name;
    private String tenantId;
    private Boolean active;
    private String licenseTier;
    private LocalDateTime createdAt;

    public static TenantResponse from(Tenant tenant) {
        TenantResponse r = new TenantResponse();
        r.setId(tenant.getId());
        r.setName(tenant.getName());
        r.setTenantId(tenant.getTenantId());
        r.setActive(tenant.getActive());
        r.setLicenseTier(tenant.getLicenseTier());
        r.setCreatedAt(tenant.getCreatedAt());
        return r;
    }
}
