package varga.supportplane.service;

import varga.supportplane.config.TenantContext;
import varga.supportplane.model.Cluster;
import varga.supportplane.model.ClusterOtp;
import varga.supportplane.model.License;
import varga.supportplane.model.Tenant;
import varga.supportplane.repository.BundleRepository;
import varga.supportplane.repository.ClusterOtpRepository;
import varga.supportplane.repository.ClusterRepository;
import varga.supportplane.repository.LicenseRepository;
import varga.supportplane.repository.RecommendationRepository;
import varga.supportplane.repository.TenantRepository;
import varga.supportplane.repository.TicketCommentRepository;
import varga.supportplane.repository.TicketRepository;
import varga.supportplane.security.OTPGenerator;
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
    private final LicenseRepository licenseRepository;
    private final BundleRepository bundleRepository;
    private final RecommendationRepository recommendationRepository;
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;

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

        // Enforce license limit
        licenseRepository.findByTenantId(tenant.getId()).ifPresent(license -> {
            long currentCount = clusterRepository.countByTenantId(tenant.getId());
            if (license.getMaxClusters() != null && currentCount >= license.getMaxClusters()) {
                throw new RuntimeException("License limit reached: maximum " + license.getMaxClusters() + " clusters allowed");
            }
        });

        // Check if cluster already exists as DISCOVERED (orphan) — adopt it
        Cluster cluster = clusterRepository.findByClusterId(clusterId).orElse(null);
        if (cluster != null && "DISCOVERED".equals(cluster.getStatus()) && cluster.getTenant() == null) {
            cluster.setTenant(tenant);
            cluster.setName(name != null ? name : cluster.getName());
            cluster.setStatus("PENDING");
            cluster = clusterRepository.save(cluster);
        } else if (cluster != null) {
            throw new RuntimeException("Cluster already registered: " + clusterId);
        } else {
            cluster = Cluster.builder()
                    .tenant(tenant)
                    .clusterId(clusterId)
                    .name(name != null ? name : clusterId)
                    .status("PENDING")
                    .build();
            cluster = clusterRepository.save(cluster);
        }

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
        Optional<Cluster> opt = clusterRepository.findById(id);
        if (opt.isPresent() && !TenantContext.isOperator()) {
            Cluster cluster = opt.get();
            if (cluster.getTenant() != null) {
                Tenant caller = getCurrentTenant();
                if (!cluster.getTenant().getId().equals(caller.getId())) {
                    return Optional.empty();
                }
            }
        }
        return opt;
    }

    public Optional<Cluster> findByClusterId(String clusterId) {
        return clusterRepository.findByClusterId(clusterId);
    }

    @Transactional
    public Cluster rename(Long id, String name) {
        Cluster cluster = clusterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));
        if (name != null && !name.isBlank()) {
            cluster.setName(name);
        }
        return clusterRepository.save(cluster);
    }

    @Transactional
    public void detach(Long id) {
        clusterRepository.findById(id).ifPresent(cluster -> {
            cluster.setStatus("DETACHED");
            clusterRepository.save(cluster);
        });
    }

    @Transactional
    public void delete(Long id) {
        Cluster cluster = clusterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + id));

        // Delete related records in correct order (FK constraints)
        List<varga.supportplane.model.Ticket> tickets = ticketRepository.findByClusterIdOrderByCreatedAtDesc(cluster.getId());
        for (varga.supportplane.model.Ticket ticket : tickets) {
            ticketCommentRepository.deleteByTicketId(ticket.getId());
        }
        ticketRepository.deleteByClusterId(cluster.getId());
        recommendationRepository.deleteByClusterId(cluster.getId());
        bundleRepository.deleteByClusterId(cluster.getId());
        clusterOtpRepository.deleteByClusterId(cluster.getId());
        clusterRepository.delete(cluster);
    }

    private Tenant getCurrentTenant() {
        String tenantId = TenantContext.getTenantId();
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
    }
}
