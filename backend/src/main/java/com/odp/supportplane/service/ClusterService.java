package com.odp.supportplane.service;

import com.odp.supportplane.config.TenantContext;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.model.ClusterOtp;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.repository.ClusterOtpRepository;
import com.odp.supportplane.repository.ClusterRepository;
import com.odp.supportplane.repository.TenantRepository;
import com.odp.supportplane.security.OTPGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final ClusterOtpRepository clusterOtpRepository;
    private final TenantRepository tenantRepository;

    @Value("${app.otp-expiry-minutes:10}")
    private int otpExpiryMinutes;

    public List<Cluster> getClusters() {
        if (TenantContext.isOperator()) {
            return clusterRepository.findAll();
        }
        Tenant tenant = getCurrentTenant();
        return clusterRepository.findByTenantId(tenant.getId());
    }

    @Transactional
    public ClusterOtp attachCluster(String clusterId, String name) {
        Tenant tenant = getCurrentTenant();

        Cluster cluster = Cluster.builder()
                .tenant(tenant)
                .clusterId(clusterId)
                .name(name != null ? name : clusterId)
                .status("PENDING")
                .build();
        cluster = clusterRepository.save(cluster);

        String otpCode = OTPGenerator.generate();
        ClusterOtp otp = ClusterOtp.builder()
                .cluster(cluster)
                .otpCode(otpCode)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .build();
        return clusterOtpRepository.save(otp);
    }

    @Transactional
    public boolean validateOtp(String clusterId, String otpCode) {
        Optional<Cluster> clusterOpt = clusterRepository.findByClusterId(clusterId);
        if (clusterOpt.isEmpty()) {
            return false;
        }

        Cluster cluster = clusterOpt.get();
        Optional<ClusterOtp> otpOpt = clusterOtpRepository
                .findByClusterIdAndOtpCodeAndUsedFalse(cluster.getId(), otpCode);

        if (otpOpt.isEmpty()) {
            return false;
        }

        ClusterOtp otp = otpOpt.get();
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        otp.setUsed(true);
        clusterOtpRepository.save(otp);

        cluster.setStatus("ACTIVE");
        cluster.setOtpValidated(true);
        clusterRepository.save(cluster);

        return true;
    }

    public Optional<Cluster> findById(Long id) {
        return clusterRepository.findById(id);
    }

    public Optional<Cluster> findByClusterId(String clusterId) {
        return clusterRepository.findByClusterId(clusterId);
    }

    @Transactional
    public void detach(Long id) {
        clusterRepository.findById(id).ifPresent(cluster -> {
            cluster.setStatus("DETACHED");
            clusterRepository.save(cluster);
        });
    }

    private Tenant getCurrentTenant() {
        String tenantId = TenantContext.getTenantId();
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
    }
}
