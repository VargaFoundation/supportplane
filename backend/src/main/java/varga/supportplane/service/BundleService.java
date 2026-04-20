package varga.supportplane.service;

import varga.supportplane.model.Bundle;
import varga.supportplane.model.Cluster;
import varga.supportplane.repository.BundleRepository;
import varga.supportplane.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BundleService {

    private final BundleRepository bundleRepository;
    private final ClusterRepository clusterRepository;
    private final BundleParsingService bundleParsingService;
    private final NotificationDispatcher notificationDispatcher;
    private final GeoIpService geoIpService;

    @Value("${app.bundle-storage-path:/var/lib/supportplane/bundles}")
    private String storagePath;

    @Transactional
    public Bundle receiveBundle(MultipartFile file, String bundleId, String clusterId,
                                 String attachmentOtp, String sourceIp) {
        if (bundleRepository.existsByBundleId(bundleId)) {
            return bundleRepository.findByBundleId(bundleId).orElse(null);
        }

        // Auto-discover cluster: if cluster_id is provided but doesn't exist, create an orphan
        Cluster cluster = null;
        if (clusterId != null && !clusterId.isBlank()) {
            cluster = clusterRepository.findByClusterId(clusterId).orElse(null);
            if (cluster == null) {
                cluster = Cluster.builder()
                        .clusterId(clusterId)
                        .name(clusterId)
                        .status("DISCOVERED")
                        .tenant(null)
                        .sourceIp(sourceIp)
                        .build();
                cluster = clusterRepository.save(cluster);
                log.info("Auto-discovered new cluster: {} from IP {}", clusterId, sourceIp);
            }
        }

        // Update source IP and geo on every bundle (IP may change)
        if (cluster != null && sourceIp != null) {
            cluster.setSourceIp(sourceIp);
            if (cluster.getGeoLocation() == null || cluster.getGeoLocation().isBlank()) {
                cluster.setGeoLocation(geoIpService.lookup(sourceIp));
            }
        }

        // Save file to disk
        String filename = file.getOriginalFilename();
        Path filePath;
        try {
            Path dir = Paths.get(storagePath);
            Files.createDirectories(dir);
            filePath = dir.resolve(bundleId + "_" + filename);
            file.transferTo(filePath);
        } catch (IOException e) {
            log.error("Failed to store bundle file: {}", e.getMessage());
            throw new RuntimeException("Failed to store bundle file", e);
        }

        Bundle bundle = Bundle.builder()
                .cluster(cluster)
                .bundleId(bundleId)
                .filename(filename)
                .filepath(filePath.toString())
                .sizeBytes(file.getSize())
                .build();
        bundle = bundleRepository.save(bundle);

        // Update cluster last bundle timestamp + notify
        if (cluster != null) {
            cluster.setLastBundleAt(LocalDateTime.now());
            clusterRepository.save(cluster);
            if (cluster.getTenant() != null) {
                notificationDispatcher.dispatch(cluster.getTenant(), "BUNDLE_RECEIVED",
                        "Bundle received for " + cluster.getName(),
                        "Bundle: " + bundleId + " (" + file.getSize() + " bytes)");
            }
        }

        log.info("Bundle received: {} (cluster: {}, ip: {}, size: {} bytes)", bundleId, clusterId, sourceIp, file.getSize());

        // Parse bundle contents to extract metadata
        bundleParsingService.parseBundle(bundle);

        return bundle;
    }

    // Backward compatible overload
    @Transactional
    public Bundle receiveBundle(MultipartFile file, String bundleId, String clusterId,
                                 String attachmentOtp) {
        return receiveBundle(file, bundleId, clusterId, attachmentOtp, null);
    }

    public List<Bundle> getBundlesForCluster(Long clusterId) {
        return bundleRepository.findByClusterIdOrderByReceivedAtDesc(clusterId);
    }

    public Optional<Bundle> findByBundleId(String bundleId) {
        return bundleRepository.findByBundleId(bundleId);
    }

    public Optional<Bundle> findById(Long id) {
        return bundleRepository.findById(id);
    }
}
