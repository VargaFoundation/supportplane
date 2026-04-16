package com.odp.supportplane.service;

import com.odp.supportplane.config.AccessControl;
import com.odp.supportplane.config.TenantContext;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.model.Recommendation;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.model.User;
import com.odp.supportplane.repository.ClusterRepository;
import com.odp.supportplane.repository.RecommendationRepository;
import com.odp.supportplane.repository.TenantRepository;
import com.odp.supportplane.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final ClusterRepository clusterRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public List<Recommendation> getAll(String status) {
        if (TenantContext.isOperator()) {
            if (status != null && !status.isBlank()) {
                return recommendationRepository.findByStatusOrderByCreatedAtDesc(status);
            }
            return recommendationRepository.findAllByOrderByCreatedAtDesc();
        }
        String tenantSlug = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findByTenantId(tenantSlug)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        return recommendationRepository.findByClusterTenantIdOrderByCreatedAtDesc(tenant.getId());
    }

    public List<Recommendation> getForCluster(Long clusterId) {
        return recommendationRepository.findByClusterIdOrderByCreatedAtDesc(clusterId);
    }

    public Optional<Recommendation> findById(Long id) {
        return recommendationRepository.findById(id);
    }

    @Transactional
    public Recommendation create(Long clusterId, String title, String description,
                                   String severity, Long createdById) {
        AccessControl.requireOperator();
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
        User createdBy = createdById != null ? userRepository.findById(createdById).orElse(null) : null;

        Recommendation rec = Recommendation.builder()
                .cluster(cluster)
                .title(title)
                .description(description)
                .severity(severity != null ? severity : "INFO")
                .createdBy(createdBy)
                .build();
        return recommendationRepository.save(rec);
    }

    @Transactional
    public Recommendation validate(Long id) {
        Recommendation rec = recommendationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recommendation not found"));
        rec.setStatus("VALIDATED");
        return recommendationRepository.save(rec);
    }

    @Transactional
    public Recommendation deliver(Long id) {
        Recommendation rec = recommendationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recommendation not found"));
        rec.setStatus("DELIVERED");
        return recommendationRepository.save(rec);
    }
}
