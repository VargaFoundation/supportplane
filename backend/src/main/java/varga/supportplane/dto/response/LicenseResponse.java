package varga.supportplane.dto.response;

import varga.supportplane.model.License;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LicenseResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String tenantSlug;
    private String tier;
    private Integer maxClusters;
    private Integer maxUsers;
    private long usedClusters;
    private long usedUsers;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime createdAt;

    public static LicenseResponse from(License license, long usedClusters, long usedUsers) {
        LicenseResponse r = new LicenseResponse();
        r.setId(license.getId());
        if (license.getTenant() != null) {
            r.setTenantId(license.getTenant().getId());
            r.setTenantName(license.getTenant().getName());
            r.setTenantSlug(license.getTenant().getTenantId());
        }
        r.setTier(license.getTier());
        r.setMaxClusters(license.getMaxClusters());
        r.setMaxUsers(license.getMaxUsers());
        r.setUsedClusters(usedClusters);
        r.setUsedUsers(usedUsers);
        r.setValidFrom(license.getValidFrom());
        r.setValidUntil(license.getValidUntil());
        r.setCreatedAt(license.getCreatedAt());
        return r;
    }
}
