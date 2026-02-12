package com.odp.supportplane.controller;

import com.odp.supportplane.dto.request.UpdateLicenseRequest;
import com.odp.supportplane.model.License;
import com.odp.supportplane.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping
    public ResponseEntity<List<License>> listLicenses() {
        return ResponseEntity.ok(licenseService.findAll());
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<License> getLicenseForTenant(@PathVariable Long tenantId) {
        return licenseService.findByTenantId(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/tenant/{tenantId}")
    public ResponseEntity<License> updateLicense(@PathVariable Long tenantId,
                                                   @RequestBody UpdateLicenseRequest request) {
        License license = licenseService.createOrUpdate(
                tenantId, request.getTier(), request.getMaxClusters(),
                request.getMaxUsers(), request.getValidFrom(), request.getValidUntil());
        return ResponseEntity.ok(license);
    }
}
