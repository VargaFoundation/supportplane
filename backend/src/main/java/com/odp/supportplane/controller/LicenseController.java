package com.odp.supportplane.controller;

import com.odp.supportplane.dto.request.UpdateLicenseRequest;
import com.odp.supportplane.dto.response.LicenseResponse;
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
    public ResponseEntity<List<LicenseResponse>> listLicenses() {
        return ResponseEntity.ok(licenseService.findAllWithUsage());
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<LicenseResponse> getLicenseForTenant(@PathVariable Long tenantId) {
        return licenseService.findByTenantId(tenantId)
                .map(l -> ResponseEntity.ok(licenseService.buildResponse(l)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<LicenseResponse> createLicense(@RequestBody UpdateLicenseRequest request) {
        License license = licenseService.createOrUpdate(
                request.getTenantId(), request.getTier(), request.getMaxClusters(),
                request.getMaxUsers(), request.getValidFrom(), request.getValidUntil());
        return ResponseEntity.ok(licenseService.buildResponse(license));
    }

    @PutMapping("/tenant/{tenantId}")
    public ResponseEntity<LicenseResponse> updateLicense(@PathVariable Long tenantId,
                                                          @RequestBody UpdateLicenseRequest request) {
        License license = licenseService.createOrUpdate(
                tenantId, request.getTier(), request.getMaxClusters(),
                request.getMaxUsers(), request.getValidFrom(), request.getValidUntil());
        return ResponseEntity.ok(licenseService.buildResponse(license));
    }
}
