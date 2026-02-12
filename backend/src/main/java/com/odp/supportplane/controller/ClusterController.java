package com.odp.supportplane.controller;

import com.odp.supportplane.dto.request.AttachClusterRequest;
import com.odp.supportplane.dto.response.AttachClusterResponse;
import com.odp.supportplane.dto.response.BundleResponse;
import com.odp.supportplane.dto.response.ClusterResponse;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.model.ClusterOtp;
import com.odp.supportplane.service.BundleService;
import com.odp.supportplane.service.ClusterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;
    private final BundleService bundleService;

    @GetMapping
    public ResponseEntity<List<ClusterResponse>> listClusters() {
        List<ClusterResponse> clusters = clusterService.getClusters().stream()
                .map(ClusterResponse::from)
                .toList();
        return ResponseEntity.ok(clusters);
    }

    @PostMapping("/attach")
    public ResponseEntity<AttachClusterResponse> attachCluster(
            @Valid @RequestBody AttachClusterRequest request) {
        ClusterOtp otp = clusterService.attachCluster(request.getClusterId(), request.getName());
        return ResponseEntity.ok(new AttachClusterResponse(
                otp.getCluster().getId(),
                otp.getOtpCode(),
                "Enter this OTP in Ambari ODPSC config (odpsc-site > attachment_otp)"
        ));
    }

    @PostMapping("/validate-otp")
    public ResponseEntity<?> validateOtp(@RequestHeader("X-ODPSC-Cluster-ID") String clusterId,
                                          @RequestHeader("X-ODPSC-Attachment-OTP") String otpCode) {
        boolean valid = clusterService.validateOtp(clusterId, otpCode);
        if (valid) {
            return ResponseEntity.ok(Map.of("status", "validated", "cluster_id", clusterId));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> detachCluster(@PathVariable Long id) {
        clusterService.detach(id);
        return ResponseEntity.ok(Map.of("status", "detached"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClusterResponse> getCluster(@PathVariable Long id) {
        return clusterService.findById(id)
                .map(c -> ResponseEntity.ok(ClusterResponse.from(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/bundles")
    public ResponseEntity<List<BundleResponse>> getClusterBundles(@PathVariable Long id) {
        List<BundleResponse> bundles = bundleService.getBundlesForCluster(id).stream()
                .map(BundleResponse::from)
                .toList();
        return ResponseEntity.ok(bundles);
    }

    @GetMapping("/{id}/topology")
    public ResponseEntity<?> getClusterTopology(@PathVariable Long id) {
        return clusterService.findById(id)
                .map(c -> ResponseEntity.ok(c.getMetadata()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<?> getClusterMetrics(@PathVariable Long id) {
        // Return latest bundle metrics from metadata
        return clusterService.findById(id)
                .map(c -> ResponseEntity.ok(c.getMetadata()))
                .orElse(ResponseEntity.notFound().build());
    }
}
