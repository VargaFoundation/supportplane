package com.odp.supportplane.service;

import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    public Tenant create(String name, String tenantId) {
        Tenant tenant = Tenant.builder()
                .name(name)
                .tenantId(tenantId)
                .build();
        return tenantRepository.save(tenant);
    }

    public Optional<Tenant> findByTenantId(String tenantId) {
        return tenantRepository.findByTenantId(tenantId);
    }

    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }

    public boolean existsByTenantId(String tenantId) {
        return tenantRepository.existsByTenantId(tenantId);
    }

    public String generateSlug(String companyName) {
        return companyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
