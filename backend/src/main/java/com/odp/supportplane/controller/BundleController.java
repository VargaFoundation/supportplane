package com.odp.supportplane.controller;

import com.odp.supportplane.dto.response.BundleResponse;
import com.odp.supportplane.model.Bundle;
import com.odp.supportplane.service.BundleService;
import com.odp.supportplane.service.ClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bundles")
@RequiredArgsConstructor
@Slf4j
public class BundleController {

    private final BundleService bundleService;
    private final ClusterService clusterService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadBundle(
            @RequestParam("bundle") MultipartFile file,
            @RequestHeader(value = "X-ODPSC-Bundle-ID", required = false) String bundleId,
            @RequestHeader(value = "X-ODPSC-Cluster-ID", required = false) String clusterId,
            @RequestHeader(value = "X-ODPSC-Attachment-OTP", required = false) String attachmentOtp) {

        if (bundleId == null || bundleId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing X-ODPSC-Bundle-ID header"));
        }

        // Validate OTP if provided
        if (attachmentOtp != null && !attachmentOtp.isBlank() && clusterId != null) {
            clusterService.validateOtp(clusterId, attachmentOtp);
        }

        Bundle bundle = bundleService.receiveBundle(file, bundleId, clusterId, attachmentOtp);
        return ResponseEntity.ok(Map.of(
                "status", "received",
                "bundle_id", bundle.getBundleId(),
                "filename", bundle.getFilename()
        ));
    }

    @GetMapping("/{bundleId}")
    public ResponseEntity<BundleResponse> getBundle(@PathVariable String bundleId) {
        return bundleService.findByBundleId(bundleId)
                .map(b -> ResponseEntity.ok(BundleResponse.from(b)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{bundleId}/download")
    public ResponseEntity<?> downloadBundle(@PathVariable String bundleId) {
        return bundleService.findByBundleId(bundleId)
                .map(bundle -> {
                    try {
                        Path path = Path.of(bundle.getFilepath());
                        if (!Files.exists(path)) {
                            return ResponseEntity.notFound().build();
                        }
                        InputStreamResource resource = new InputStreamResource(new FileInputStream(path.toFile()));
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bundle.getFilename() + "\"")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .contentLength(Files.size(path))
                                .body(resource);
                    } catch (Exception e) {
                        log.error("Failed to serve bundle file: {}", e.getMessage());
                        return ResponseEntity.internalServerError().body(Map.of("error", "Failed to download bundle"));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
