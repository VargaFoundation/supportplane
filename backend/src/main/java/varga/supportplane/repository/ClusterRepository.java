package varga.supportplane.repository;

import varga.supportplane.model.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClusterRepository extends JpaRepository<Cluster, Long> {
    List<Cluster> findByTenantId(Long tenantId);
    Optional<Cluster> findByClusterId(String clusterId);
    List<Cluster> findByStatus(String status);
    long countByTenantId(Long tenantId);
}
