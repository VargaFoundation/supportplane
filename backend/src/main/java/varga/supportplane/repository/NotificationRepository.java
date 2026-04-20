package varga.supportplane.repository;

import varga.supportplane.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByTenantId(Long tenantId);
    List<Notification> findByTenantIdAndEnabledTrue(Long tenantId);
}
