package varga.supportplane.dto.response;

import varga.supportplane.model.Cluster;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClusterResponse {
    private Long id;
    private String clusterId;
    private String name;
    private String status;
    private Boolean otpValidated;
    private String tenantName;
    private Long tenantId;
    private String sourceIp;
    private String geoLocation;
    private LocalDateTime lastBundleAt;
    private LocalDateTime createdAt;

    public static ClusterResponse from(Cluster cluster) {
        ClusterResponse r = new ClusterResponse();
        r.setId(cluster.getId());
        r.setClusterId(cluster.getClusterId());
        r.setName(cluster.getName());
        r.setStatus(cluster.getStatus());
        r.setOtpValidated(cluster.getOtpValidated());
        if (cluster.getTenant() != null) {
            r.setTenantName(cluster.getTenant().getName());
            r.setTenantId(cluster.getTenant().getId());
        }
        r.setSourceIp(cluster.getSourceIp());
        r.setGeoLocation(cluster.getGeoLocation());
        r.setLastBundleAt(cluster.getLastBundleAt());
        r.setCreatedAt(cluster.getCreatedAt());
        return r;
    }
}
