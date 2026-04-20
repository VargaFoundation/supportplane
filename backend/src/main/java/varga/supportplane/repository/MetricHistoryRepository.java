package varga.supportplane.repository;

import varga.supportplane.model.MetricHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MetricHistoryRepository extends JpaRepository<MetricHistory, Long> {

    List<MetricHistory> findTop200ByClusterIdOrderByCollectedAtDesc(Long clusterId);

    long countByClusterId(Long clusterId);

    @Modifying
    @Query("DELETE FROM MetricHistory m WHERE m.cluster.id = :clusterId AND m.collectedAt < :before")
    void deleteByClusterIdAndCollectedAtBefore(Long clusterId, LocalDateTime before);
}
