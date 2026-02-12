package com.odp.supportplane.service;

import com.odp.supportplane.config.TenantContext;
import com.odp.supportplane.dto.response.DashboardResponse;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ClusterRepository clusterRepository;
    private final BundleRepository bundleRepository;
    private final TicketRepository ticketRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public DashboardResponse getDashboard() {
        if (TenantContext.isOperator()) {
            return getOperatorDashboard();
        }
        return getClientDashboard();
    }

    private DashboardResponse getOperatorDashboard() {
        return DashboardResponse.builder()
                .totalClusters(clusterRepository.count())
                .activeClusters(clusterRepository.findByStatus("ACTIVE").size())
                .totalBundles(bundleRepository.count())
                .openTickets(ticketRepository.findByStatus("OPEN").size()
                        + ticketRepository.findByStatus("ASSIGNED").size())
                .pendingRecommendations(recommendationRepository.findByStatus("DRAFT").size())
                .totalUsers(userRepository.count())
                .totalTenants(tenantRepository.count())
                .build();
    }

    private DashboardResponse getClientDashboard() {
        String tenantId = TenantContext.getTenantId();
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        long clusters = clusterRepository.findByTenantId(tenant.getId()).size();
        long activeClusters = clusterRepository.findByTenantId(tenant.getId()).stream()
                .filter(c -> "ACTIVE".equals(c.getStatus())).count();
        long openTickets = ticketRepository.countByTenantIdAndStatus(tenant.getId(), "OPEN");
        long users = userRepository.findByTenantId(tenant.getId()).size();

        return DashboardResponse.builder()
                .totalClusters(clusters)
                .activeClusters(activeClusters)
                .openTickets(openTickets)
                .totalUsers(users)
                .build();
    }
}
