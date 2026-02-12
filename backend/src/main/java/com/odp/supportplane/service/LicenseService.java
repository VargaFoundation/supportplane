package com.odp.supportplane.service;

import com.odp.supportplane.model.License;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.repository.LicenseRepository;
import com.odp.supportplane.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LicenseService {

    private final LicenseRepository licenseRepository;
    private final TenantRepository tenantRepository;

    public List<License> findAll() {
        return licenseRepository.findAll();
    }

    public Optional<License> findByTenantId(Long tenantId) {
        return licenseRepository.findByTenantId(tenantId);
    }

    @Transactional
    public License createOrUpdate(Long tenantId, String tier, Integer maxClusters,
                                    Integer maxUsers, LocalDateTime validFrom,
                                    LocalDateTime validUntil) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        License license = licenseRepository.findByTenantId(tenantId)
                .orElse(License.builder().tenant(tenant).build());

        if (tier != null) license.setTier(tier);
        if (maxClusters != null) license.setMaxClusters(maxClusters);
        if (maxUsers != null) license.setMaxUsers(maxUsers);
        if (validFrom != null) license.setValidFrom(validFrom);
        if (validUntil != null) license.setValidUntil(validUntil);

        license = licenseRepository.save(license);

        tenant.setLicenseTier(license.getTier());
        tenantRepository.save(tenant);

        return license;
    }
}
