package com.odp.supportplane.controller;

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
        List<TenantResponse> tenants = tenantService.findAll().stream()
                .map(TenantResponse::from)
                .toList();
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String tenantId) {
        return tenantService.findByTenantId(tenantId)
                .map(t -> ResponseEntity.ok(TenantResponse.from(t)))
                .orElse(ResponseEntity.notFound().build());
    }
}
