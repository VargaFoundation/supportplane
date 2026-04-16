package com.odp.supportplane.repository;

import com.odp.supportplane.model.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BundleRepository extends JpaRepository<Bundle, Long> {
    List<Bundle> findByClusterIdOrderByReceivedAtDesc(Long clusterId);
    Optional<Bundle> findByBundleId(String bundleId);
    boolean existsByBundleId(String bundleId);
    void deleteByClusterId(Long clusterId);
    long countByClusterTenantId(Long tenantId);
}
