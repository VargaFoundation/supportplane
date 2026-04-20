package varga.supportplane.repository;

import varga.supportplane.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<AuditEvent> findAllByOrderByCreatedAtDesc();
    List<AuditEvent> findTop50ByOrderByCreatedAtDesc();
    List<AuditEvent> findTop50ByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
