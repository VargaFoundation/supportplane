package com.odp.supportplane.controller;

import com.odp.supportplane.config.TenantContext;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.model.Ticket;
import com.odp.supportplane.repository.ClusterRepository;
import com.odp.supportplane.repository.TenantRepository;
import com.odp.supportplane.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final TenantRepository tenantRepository;
    private final ClusterRepository clusterRepository;
    private final TicketRepository ticketRepository;

    @GetMapping
    public ResponseEntity<Map<String, List<Map<String, Object>>>> search(@RequestParam String q) {
        if (q == null || q.isBlank() || q.length() < 2) {
            return ResponseEntity.ok(Map.of("tenants", List.of(), "clusters", List.of(), "tickets", List.of()));
        }

        String lower = q.toLowerCase();
        boolean isOperator = TenantContext.isOperator();

        // Tenants (operator only)
        List<Map<String, Object>> tenantResults = new ArrayList<>();
        if (isOperator) {
            tenantRepository.findAll().stream()
                    .filter(t -> matches(t.getName(), lower) || matches(t.getTenantId(), lower)
                            || matches(t.getClientName(), lower))
                    .limit(5)
                    .forEach(t -> tenantResults.add(Map.of(
                            "id", t.getId(), "name", t.getName(),
                            "tenantId", t.getTenantId(), "type", "tenant")));
        }

        // Clusters
        List<Cluster> allClusters = isOperator ? clusterRepository.findAll()
                : clusterRepository.findByTenantId(getCurrentTenantId());
        List<Map<String, Object>> clusterResults = allClusters.stream()
                .filter(c -> matches(c.getName(), lower) || matches(c.getClusterId(), lower))
                .limit(5)
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(), "name", c.getName() != null ? c.getName() : c.getClusterId(),
                        "clusterId", c.getClusterId(), "status", c.getStatus(), "type", "cluster"))
                .toList();

        // Tickets
        List<Ticket> allTickets = isOperator ? ticketRepository.findAllByOrderByCreatedAtDesc()
                : ticketRepository.findByTenantIdOrderByCreatedAtDesc(getCurrentTenantId());
        List<Map<String, Object>> ticketResults = allTickets.stream()
                .filter(t -> matches(t.getTitle(), lower) || matches(t.getDescription(), lower))
                .limit(5)
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(), "title", t.getTitle(),
                        "status", t.getStatus(), "priority", t.getPriority(), "type", "ticket"))
                .toList();

        return ResponseEntity.ok(Map.of("tenants", tenantResults, "clusters", clusterResults, "tickets", ticketResults));
    }

    private boolean matches(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private Long getCurrentTenantId() {
        String slug = TenantContext.getTenantId();
        return tenantRepository.findByTenantId(slug).map(Tenant::getId).orElse(-1L);
    }
}
