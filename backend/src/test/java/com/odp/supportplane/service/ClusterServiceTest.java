package com.odp.supportplane.service;

import com.odp.supportplane.config.TenantContext;
import com.odp.supportplane.config.TestSecurityConfig;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.model.ClusterOtp;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.repository.ClusterOtpRepository;
import com.odp.supportplane.repository.ClusterRepository;
import com.odp.supportplane.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ClusterServiceTest {

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private ClusterOtpRepository clusterOtpRepository;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(
                Tenant.builder().name("Test Corp").tenantId("test-tenant").build());
        TenantContext.setTenantId("test-tenant");
        TenantContext.setRole("ADMIN");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        clusterOtpRepository.deleteAll();
        clusterRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void attachCluster_createsClusterAndOtp() {
        ClusterOtp otp = clusterService.attachCluster("cl-001", "Production");

        assertNotNull(otp);
        assertNotNull(otp.getOtpCode());
        assertEquals(6, otp.getOtpCode().length());
        assertEquals("PENDING", otp.getCluster().getStatus());
        assertEquals("Production", otp.getCluster().getName());
    }

    @Test
    void validateOtp_success() {
        ClusterOtp otp = clusterService.attachCluster("cl-002", "Staging");
        boolean valid = clusterService.validateOtp("cl-002", otp.getOtpCode());

        assertTrue(valid);

        Cluster cluster = clusterRepository.findByClusterId("cl-002").orElseThrow();
        assertEquals("ACTIVE", cluster.getStatus());
        assertTrue(cluster.getOtpValidated());
    }

    @Test
    void validateOtp_wrongCode_returnsFalse() {
        clusterService.attachCluster("cl-003", "Dev");
        boolean valid = clusterService.validateOtp("cl-003", "000000");

        assertFalse(valid);
    }

    @Test
    void validateOtp_expiredOtp_returnsFalse() {
        ClusterOtp otp = clusterService.attachCluster("cl-004", "Test");
        // Manually expire the OTP
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        clusterOtpRepository.save(otp);

        boolean valid = clusterService.validateOtp("cl-004", otp.getOtpCode());
        assertFalse(valid);
    }

    @Test
    void validateOtp_nonexistentCluster_returnsFalse() {
        assertFalse(clusterService.validateOtp("nonexistent", "123456"));
    }

    @Test
    void getClusters_tenantScoped() {
        clusterService.attachCluster("cl-t1", "C1");
        clusterService.attachCluster("cl-t2", "C2");

        List<Cluster> clusters = clusterService.getClusters();
        assertEquals(2, clusters.size());
    }

    @Test
    void getClusters_operatorSeesAll() {
        clusterService.attachCluster("cl-op1", "C1");
        TenantContext.setRole("OPERATOR");

        List<Cluster> clusters = clusterService.getClusters();
        assertFalse(clusters.isEmpty());
    }

    @Test
    void detach_setsStatusToDetached() {
        ClusterOtp otp = clusterService.attachCluster("cl-detach", "Detach Me");
        Long clusterId = otp.getCluster().getId();

        clusterService.detach(clusterId);

        Cluster cluster = clusterRepository.findById(clusterId).orElseThrow();
        assertEquals("DETACHED", cluster.getStatus());
    }

    @Test
    void findById_returnsCluster() {
        ClusterOtp otp = clusterService.attachCluster("cl-find", "Find Me");
        Long id = otp.getCluster().getId();

        assertTrue(clusterService.findById(id).isPresent());
        assertFalse(clusterService.findById(999L).isPresent());
    }
}
