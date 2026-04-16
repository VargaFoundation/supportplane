package com.odp.supportplane.service;

import com.odp.supportplane.model.Bundle;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.repository.BundleRepository;
import com.odp.supportplane.repository.ClusterRepository;
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

    @Value("${app.bundle-storage-path:/var/lib/supportplane/bundles}")
    private String storagePath;

    @Transactional
    public Bundle receiveBundle(MultipartFile file, String bundleId, String clusterId,
                                 String attachmentOtp) {
        if (bundleRepository.existsByBundleId(bundleId)) {
            return bundleRepository.findByBundleId(bundleId).orElse(null);
        }

        Optional<Cluster> clusterOpt = clusterRepository.findByClusterId(clusterId);
        Cluster cluster = clusterOpt.orElse(null);

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

        log.info("Bundle received: {} (cluster: {}, size: {} bytes)", bundleId, clusterId, file.getSize());

        // Parse bundle contents to extract metadata
        bundleParsingService.parseBundle(bundle);

        return bundle;
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
