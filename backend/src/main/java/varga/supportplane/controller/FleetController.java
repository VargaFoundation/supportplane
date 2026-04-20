package varga.supportplane.controller;

import varga.supportplane.config.AccessControl;
import varga.supportplane.model.Cluster;
import varga.supportplane.repository.BundleRepository;
import varga.supportplane.repository.ClusterRepository;
import varga.supportplane.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fleet-wide statistics for operators.
 * Aggregates metadata from all clusters to give a global view of the installed base.
 */
@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
public class FleetController {

    private final ClusterRepository clusterRepository;
    private final TenantRepository tenantRepository;
    private final BundleRepository bundleRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getFleetStats() {
        AccessControl.requireOperator();

        List<Cluster> allClusters = clusterRepository.findAll();
        long totalBundles = bundleRepository.count();
        long totalTenants = tenantRepository.count();

        // Status distribution
        Map<String, Long> byStatus = allClusters.stream()
                .collect(Collectors.groupingBy(Cluster::getStatus, Collectors.counting()));

        // Stack version distribution (from metadata.topology.cluster_info.desired_stack_id)
        Map<String, Long> byVersion = new TreeMap<>();
        // OS distribution (from metadata.system_info.os.system + release)
        Map<String, Long> byOs = new TreeMap<>();
        // Geo distribution
        Map<String, Long> byGeo = new TreeMap<>();
        // Host counts for average cluster size
        List<Integer> hostCounts = new ArrayList<>();
        // Total servers
        int totalServers = 0;

        for (Cluster c : allClusters) {
            Map<String, Object> meta = c.getMetadata();
            if (meta == null) continue;

            // Stack version
            String version = extractString(meta, "topology", "cluster_info", "desired_stack_id");
            if (version != null && !version.isBlank()) {
                byVersion.merge(version, 1L, Long::sum);
            }

            // Host count - try total_hosts first, then count hosts array
            Integer hosts = extractInt(meta, "topology", "cluster_info", "total_hosts");
            if (hosts == null || hosts == 0) {
                List<?> hostList = extractList(meta, "topology", "hosts");
                if (hostList != null && !hostList.isEmpty()) {
                    hosts = hostList.size();
                }
            }
            if (hosts != null && hosts > 0) {
                hostCounts.add(hosts);
                totalServers += hosts;
            }

            // OS info from system_info
            String osSystem = extractString(meta, "system_info", "os", "system");
            String osRelease = extractString(meta, "system_info", "os", "release");
            if (osSystem != null) {
                String osKey = osSystem + (osRelease != null ? " " + osRelease : "");
                byOs.merge(osKey, 1L, Long::sum);
            }

            // Geo location
            if (c.getGeoLocation() != null && !c.getGeoLocation().isBlank()) {
                // Extract country (last part after last comma)
                String[] parts = c.getGeoLocation().split(",");
                String country = parts[parts.length - 1].trim();
                // Remove ISP part in parentheses
                if (country.contains("(")) country = country.substring(0, country.indexOf('(')).trim();
                if (!country.isBlank()) {
                    byGeo.merge(country, 1L, Long::sum);
                }
            }
        }

        double avgClusterSize = hostCounts.isEmpty() ? 0 :
                hostCounts.stream().mapToInt(Integer::intValue).average().orElse(0);

        // Cluster locations (for map/list)
        List<Map<String, Object>> clusterLocations = allClusters.stream()
                .filter(c -> c.getSourceIp() != null)
                .map(c -> {
                    Map<String, Object> loc = new LinkedHashMap<>();
                    loc.put("id", c.getId());
                    loc.put("name", c.getName());
                    loc.put("clusterId", c.getClusterId());
                    loc.put("status", c.getStatus());
                    loc.put("sourceIp", c.getSourceIp());
                    loc.put("geoLocation", c.getGeoLocation());
                    loc.put("tenantName", c.getTenant() != null ? c.getTenant().getName() : null);
                    // Extract version
                    if (c.getMetadata() != null) {
                        loc.put("stackVersion", extractString(c.getMetadata(), "topology", "cluster_info", "desired_stack_id"));
                        Integer h = extractInt(c.getMetadata(), "topology", "cluster_info", "total_hosts");
                        loc.put("hostCount", h != null ? h : 0);
                    }
                    return loc;
                })
                .toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalClusters", allClusters.size());
        stats.put("totalTenants", totalTenants);
        stats.put("totalBundles", totalBundles);
        stats.put("totalServers", totalServers);
        stats.put("avgClusterSize", Math.round(avgClusterSize * 10.0) / 10.0);
        stats.put("byStatus", byStatus);
        stats.put("byVersion", byVersion);
        stats.put("byOs", byOs);
        stats.put("byGeo", byGeo);
        stats.put("clusters", clusterLocations);

        return ResponseEntity.ok(stats);
    }

    /**
     * Navigate a JSON structure with path keys.
     * Handles aggregated bundles where values may be wrapped in a single-element list.
     */
    @SuppressWarnings("unchecked")
    private Object navigate(Object current, String... path) {
        for (String key : path) {
            // Unwrap single-element arrays (from aggregated bundles)
            if (current instanceof List<?> list && !list.isEmpty()) {
                current = list.get(0);
            }
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }
        // Final unwrap
        if (current instanceof List<?> list && !list.isEmpty() && list.size() == 1) {
            current = list.get(0);
        }
        return current;
    }

    private String extractString(Map<String, Object> meta, String... path) {
        Object val = navigate(meta, path);
        return val instanceof String s ? s : null;
    }

    private Integer extractInt(Map<String, Object> meta, String... path) {
        Object val = navigate(meta, path);
        if (val instanceof Number n) return n.intValue();
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<?> extractList(Map<String, Object> meta, String... path) {
        Object val = navigate(meta, path);
        return val instanceof List<?> l ? l : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> meta, String... path) {
        Object val = navigate(meta, path);
        return val instanceof Map ? (Map<String, Object>) val : null;
    }
}
