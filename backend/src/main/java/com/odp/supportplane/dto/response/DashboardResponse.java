package com.odp.supportplane.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {
    private long totalClusters;
    private long activeClusters;
    private long totalBundles;
    private long openTickets;
    private long pendingRecommendations;
    private long totalUsers;
    private long totalTenants;
}
