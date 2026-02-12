package com.odp.supportplane.repository;

import com.odp.supportplane.model.License;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {
    Optional<License> findByTenantId(Long tenantId);
}
