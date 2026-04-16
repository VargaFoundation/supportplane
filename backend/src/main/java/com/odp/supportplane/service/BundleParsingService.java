package com.odp.supportplane.service;

import com.fasterxml.jackson.core.type.TypeReference;
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

            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory()) continue;

                    byte[] content = zis.readAllBytes();

                    switch (name) {
                        case "manifest.json" -> bundleMetadata.put("manifest",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "topology.json" -> clusterMetadata.put("topology",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "service_health.json" -> clusterMetadata.put("service_health",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "system_info.json" -> clusterMetadata.put("system_info",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "kerberos_status.json" -> clusterMetadata.put("kerberos_status",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "ssl_certs.json" -> clusterMetadata.put("ssl_certs",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "kernel_params.json" -> clusterMetadata.put("kernel_params",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "metrics.json" -> clusterMetadata.put("metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "jmx_metrics.json" -> clusterMetadata.put("jmx_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "granular_metrics.json" -> clusterMetadata.put("granular_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "yarn_queues.json" -> clusterMetadata.put("yarn_queues",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "hdfs_report.json" -> clusterMetadata.put("hdfs_report",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "config_drift.json" -> clusterMetadata.put("config_drift",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "benchmarks.json" -> clusterMetadata.put("benchmarks",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "alert_history.json" -> clusterMetadata.put("alert_history",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "hbase_metrics.json" -> clusterMetadata.put("hbase_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "impala_metrics.json" -> clusterMetadata.put("impala_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "kudu_metrics.json" -> clusterMetadata.put("kudu_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "hive_metrics.json" -> clusterMetadata.put("hive_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "zookeeper_metrics.json" -> clusterMetadata.put("zookeeper_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "atlas_metrics.json" -> clusterMetadata.put("atlas_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "ranger_metrics.json" -> clusterMetadata.put("ranger_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "nifi_metrics.json" -> clusterMetadata.put("nifi_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "kafka_metrics.json" -> clusterMetadata.put("kafka_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "spark_metrics.json" -> clusterMetadata.put("spark_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "oozie_metrics.json" -> clusterMetadata.put("oozie_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        case "solr_metrics.json" -> clusterMetadata.put("solr_metrics",
                                objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {}));
                        default -> {
                            // Skip config files and other entries
                        }
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
