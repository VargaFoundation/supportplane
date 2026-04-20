package varga.supportplane.repository;

import varga.supportplane.model.AuditRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditRunRepository extends JpaRepository<AuditRun, Long> {

    List<AuditRun> findByClusterIdOrderByStartedAtDesc(Long clusterId);

    Optional<AuditRun> findFirstByClusterIdOrderByStartedAtDesc(Long clusterId);
}
