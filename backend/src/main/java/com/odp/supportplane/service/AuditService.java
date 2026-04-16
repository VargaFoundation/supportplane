package com.odp.supportplane.service;

import com.odp.supportplane.config.TenantContext;
import com.odp.supportplane.model.AuditEvent;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.repository.AuditEventRepository;
import com.odp.supportplane.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final TenantRepository tenantRepository;

    /**
     * Log an audit event. Called from services after mutations.
     * Runs async to not slow down the main request.
     */
    public void log(String action, String targetType, String targetId, String targetLabel, String details) {
        try {
            String actor = TenantContext.getTenantId();
            String actorRole = TenantContext.getRole();
            Tenant tenant = null;

            // Resolve tenant for the event
            if (actor != null && !"support".equals(actor)) {
                tenant = tenantRepository.findByTenantId(actor).orElse(null);
            }

            AuditEvent event = AuditEvent.builder()
                    .tenant(tenant)
                    .actor(actor)
                    .actorRole(actorRole)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetLabel(targetLabel)
                    .details(details)
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to log audit event: {}", e.getMessage());
        }
    }

    /**
     * Log an audit event for a specific tenant (used when operator acts on a tenant).
     */
    public void logForTenant(Tenant tenant, String action, String targetType, String targetId, String targetLabel) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .tenant(tenant)
                    .actor(TenantContext.getTenantId())
                    .actorRole(TenantContext.getRole())
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetLabel(targetLabel)
                    .build();
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to log audit event: {}", e.getMessage());
        }
    }

    public List<AuditEvent> getEvents() {
        if (TenantContext.isOperator()) {
            return auditEventRepository.findTop50ByOrderByCreatedAtDesc();
        }
        String tenantSlug = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findByTenantId(tenantSlug).orElse(null);
        if (tenant == null) return List.of();
        return auditEventRepository.findTop50ByTenantIdOrderByCreatedAtDesc(tenant.getId());
    }
}
