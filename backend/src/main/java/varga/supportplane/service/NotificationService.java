package varga.supportplane.service;

import varga.supportplane.config.AccessControl;
import varga.supportplane.config.TenantContext;
import varga.supportplane.model.Notification;
import varga.supportplane.model.Tenant;
import varga.supportplane.repository.NotificationRepository;
import varga.supportplane.repository.TenantRepository;
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
        AccessControl.requireAdminOrOperator();
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

        // Verify tenant owns this notification
        if (!TenantContext.isOperator() && notification.getTenant() != null) {
            Tenant callerTenant = getCurrentTenant();
            if (!notification.getTenant().getId().equals(callerTenant.getId())) {
                throw new RuntimeException("Notification config not found");
            }
        }

        if (type != null) notification.setType(type);
        if (channel != null) notification.setChannel(channel);
        if (config != null) notification.setConfig(config);
        if (enabled != null) notification.setEnabled(enabled);

        return notificationRepository.save(notification);
    }

    @Transactional
    public void delete(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification config not found"));
        if (!TenantContext.isOperator() && notification.getTenant() != null) {
            Tenant callerTenant = getCurrentTenant();
            if (!notification.getTenant().getId().equals(callerTenant.getId())) {
                throw new RuntimeException("Notification config not found");
            }
        }
        notificationRepository.delete(notification);
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
