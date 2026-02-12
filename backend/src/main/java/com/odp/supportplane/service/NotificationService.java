package com.odp.supportplane.service;

import com.odp.supportplane.config.TenantContext;
import com.odp.supportplane.model.Notification;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.repository.NotificationRepository;
import com.odp.supportplane.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TenantRepository tenantRepository;

    public List<Notification> getConfigs() {
        Tenant tenant = getCurrentTenant();
        return notificationRepository.findByTenantId(tenant.getId());
    }

    @Transactional
    public Notification create(String type, String channel, Map<String, Object> config) {
        Tenant tenant = getCurrentTenant();

        Notification notification = Notification.builder()
                .tenant(tenant)
                .type(type)
                .channel(channel)
                .config(config)
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification update(Long id, String type, String channel,
                                Map<String, Object> config, Boolean enabled) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification config not found"));

        if (type != null) notification.setType(type);
        if (channel != null) notification.setChannel(channel);
        if (config != null) notification.setConfig(config);
        if (enabled != null) notification.setEnabled(enabled);

        return notificationRepository.save(notification);
    }

    @Transactional
    public void delete(Long id) {
        notificationRepository.deleteById(id);
    }

    public Optional<Notification> findById(Long id) {
        return notificationRepository.findById(id);
    }

    private Tenant getCurrentTenant() {
        String tenantId = TenantContext.getTenantId();
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
    }
}
