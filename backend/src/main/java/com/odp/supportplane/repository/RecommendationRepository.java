package com.odp.supportplane.repository;

import com.odp.supportplane.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByClusterIdOrderByCreatedAtDesc(Long clusterId);
    List<Recommendation> findByStatus(String status);
    void deleteByClusterId(Long clusterId);
    List<Recommendation> findAllByOrderByCreatedAtDesc();
    List<Recommendation> findByStatusOrderByCreatedAtDesc(String status);
    List<Recommendation> findByClusterTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<Recommendation> findByClusterIdAndSourceOrderByCategoryAscComponentAsc(Long clusterId, String source);

    void deleteByClusterIdAndSource(Long clusterId, String source);

    long countByClusterIdAndFindingStatus(Long clusterId, String findingStatus);
}
