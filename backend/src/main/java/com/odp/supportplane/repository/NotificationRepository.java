package com.odp.supportplane.repository;

import com.odp.supportplane.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByTenantId(Long tenantId);
    List<Notification> findByTenantIdAndEnabledTrue(Long tenantId);
}
