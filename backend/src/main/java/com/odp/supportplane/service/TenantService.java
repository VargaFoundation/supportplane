package com.odp.supportplane.service;

import com.odp.supportplane.config.AccessControl;
import com.odp.supportplane.dto.request.UpdateTenantRequest;
import com.odp.supportplane.dto.response.TenantResponse;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.repository.ClusterRepository;
import com.odp.supportplane.repository.TenantRepository;
import com.odp.supportplane.repository.TicketRepository;
import com.odp.supportplane.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ClusterRepository clusterRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final AuditService auditService;

    public Tenant create(String name, String tenantId) {
        Tenant tenant = Tenant.builder()
                .name(name)
                .tenantId(tenantId)
                .build();
        return tenantRepository.save(tenant);
    }

    @Transactional
    public Tenant update(String tenantIdSlug, UpdateTenantRequest req) {
        AccessControl.requireOperator();
        Tenant tenant = tenantRepository.findByTenantId(tenantIdSlug)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantIdSlug));

        if (req.getName() != null) tenant.setName(req.getName());
        if (req.getClientName() != null) tenant.setClientName(req.getClientName());
        if (req.getSupportLevel() != null) tenant.setSupportLevel(req.getSupportLevel());
        if (req.getContractReference() != null) tenant.setContractReference(req.getContractReference());
        if (req.getContractFramework() != null) tenant.setContractFramework(req.getContractFramework());
        if (req.getContractEndDate() != null) tenant.setContractEndDate(req.getContractEndDate());
        if (req.getNotes() != null) tenant.setNotes(req.getNotes());
        if (req.getActive() != null) tenant.setActive(req.getActive());

        tenant = tenantRepository.save(tenant);
        auditService.logForTenant(tenant, "TENANT_UPDATED", "TENANT", tenantIdSlug, tenant.getName());
        return tenant;
    }

    public TenantResponse buildResponse(Tenant tenant) {
        long clusters = clusterRepository.countByTenantId(tenant.getId());
        long users = userRepository.countByTenantIdAndActiveTrue(tenant.getId());
        long openTickets = ticketRepository.countByTenantIdAndStatus(tenant.getId(), "OPEN");
        return TenantResponse.from(tenant, clusters, users, openTickets);
    }

    public List<TenantResponse> findAllWithCounts() {
        return tenantRepository.findAll().stream()
                .map(this::buildResponse)
                .toList();
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
