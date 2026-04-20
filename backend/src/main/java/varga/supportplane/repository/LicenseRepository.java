package varga.supportplane.repository;

import varga.supportplane.model.License;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {
    Optional<License> findByTenantId(Long tenantId);
}
