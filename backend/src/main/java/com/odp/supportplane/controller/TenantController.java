package com.odp.supportplane.controller;

import com.odp.supportplane.dto.request.UpdateTenantRequest;
import com.odp.supportplane.dto.response.TenantResponse;
import com.odp.supportplane.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    public ResponseEntity<List<TenantResponse>> listTenants() {
        return ResponseEntity.ok(tenantService.findAllWithCounts());
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String tenantId) {
        return tenantService.findByTenantId(tenantId)
                .map(t -> ResponseEntity.ok(tenantService.buildResponse(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> updateTenant(@PathVariable String tenantId,
                                                        @RequestBody UpdateTenantRequest request) {
        var tenant = tenantService.update(tenantId, request);
        return ResponseEntity.ok(tenantService.buildResponse(tenant));
    }
}
