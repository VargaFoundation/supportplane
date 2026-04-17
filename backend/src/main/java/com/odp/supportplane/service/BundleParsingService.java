package com.odp.supportplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odp.supportplane.model.Bundle;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.spec.KeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BundleParsingService {

    private final ClusterRepository clusterRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.encryption-key:}")
    private String encryptionKey;

    private static final byte[] SALT = "odpsc-v2-salt".getBytes();
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int AES_KEY_LENGTH = 256;
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    @Transactional
    public void parseBundle(Bundle bundle) {
        try {
            byte[] zipData = readBundleData(bundle.getFilepath());
            if (zipData == null) return;

            Map<String, Object> bundleMetadata = new HashMap<>();
            Map<String, Object> clusterMetadata = new HashMap<>();

            // JSON files that go into bundle metadata vs cluster metadata
            var bundleFiles = java.util.Set.of("manifest.json");
            var clusterFiles = java.util.Set.of("topology.json", "service_health.json", "system_info.json",
                    "kerberos_status.json", "ssl_certs.json", "kernel_params.json", "metrics.json",
                    "jmx_metrics.json", "granular_metrics.json", "yarn_queues.json", "hdfs_report.json",
                    "config_drift.json", "benchmarks.json", "alert_history.json", "hbase_metrics.json",
                    "impala_metrics.json", "kudu_metrics.json", "hive_metrics.json", "zookeeper_metrics.json",
                    "atlas_metrics.json", "ranger_metrics.json", "nifi_metrics.json", "kafka_metrics.json",
                    "spark_metrics.json", "oozie_metrics.json", "solr_metrics.json", "log_tails.json");

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory()) continue;

                    // Parse JSON files generically (handles both maps and arrays at root)
                    if (name.endsWith(".json")) {
                        try {
                            byte[] content = zis.readAllBytes();
                            String key = name.replace(".json", "");
                            Object parsed = objectMapper.readValue(content, Object.class);
                            if (bundleFiles.contains(name)) {
                                bundleMetadata.put(key, parsed);
                            } else if (clusterFiles.contains(name)) {
                                clusterMetadata.put(key, parsed);
                            }
                        } catch (Exception e) {
                            log.debug("Skipping unparseable JSON: {}", name);
                        }
                        continue;
                    }
                }
            }

            // Store manifest as bundle metadata
            if (!bundleMetadata.isEmpty()) {
                bundle.setMetadata(bundleMetadata);
            }

            // Update cluster metadata with latest topology/health
            if (bundle.getCluster() != null && !clusterMetadata.isEmpty()) {
                Cluster cluster = bundle.getCluster();
                Map<String, Object> existing = cluster.getMetadata();
                if (existing == null) existing = new HashMap<>();
                existing.putAll(clusterMetadata);
                cluster.setMetadata(existing);
                clusterRepository.save(cluster);
            }

            log.info("Parsed bundle {} - extracted {} bundle fields, {} cluster fields",
                    bundle.getBundleId(), bundleMetadata.size(), clusterMetadata.size());

        } catch (Exception e) {
            log.warn("Failed to parse bundle {}: {}", bundle.getBundleId(), e.getMessage());
        }
    }

    private byte[] readBundleData(String filepath) {
        try {
            Path path = Path.of(filepath);
            if (!Files.exists(path)) {
                log.warn("Bundle file not found: {}", filepath);
                return null;
            }

            byte[] raw = Files.readAllBytes(path);

            // If encrypted (.enc extension), decrypt
            if (filepath.endsWith(".enc") && encryptionKey != null && !encryptionKey.isBlank()) {
                return decrypt(raw, encryptionKey);
            }

            return raw;
        } catch (Exception e) {
            log.warn("Failed to read bundle file {}: {}", filepath, e.getMessage());
            return null;
        }
    }

    /**
     * Decrypt AES-256-GCM encrypted data using PBKDF2-derived key.
     * Format: 12-byte nonce + ciphertext (includes GCM tag).
     * Matches odpsc_master.py encrypt_bundle/decrypt_bundle.
     */
    private byte[] decrypt(byte[] encryptedData, String keyMaterial) throws Exception {
        // Derive key using PBKDF2-HMAC-SHA256
        KeySpec spec = new PBEKeySpec(keyMaterial.toCharArray(), SALT, PBKDF2_ITERATIONS, AES_KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

        // Extract nonce (first 12 bytes) and ciphertext
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        System.arraycopy(encryptedData, 0, nonce, 0, GCM_NONCE_LENGTH);
        byte[] ciphertext = new byte[encryptedData.length - GCM_NONCE_LENGTH];
        System.arraycopy(encryptedData, GCM_NONCE_LENGTH, ciphertext, 0, ciphertext.length);

        // Decrypt
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return cipher.doFinal(ciphertext);
    }
}
