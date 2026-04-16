package com.odp.supportplane.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odp.supportplane.TestHelper;
import com.odp.supportplane.config.SecurityConfig;
import com.odp.supportplane.config.TenantFilter;
import com.odp.supportplane.dto.request.AttachClusterRequest;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.model.ClusterOtp;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.service.BundleService;
import com.odp.supportplane.service.ClusterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClusterController.class)
@Import({SecurityConfig.class, TenantFilter.class})
class ClusterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClusterService clusterService;

    @MockBean
    private BundleService bundleService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private Tenant testTenant() {
        return Tenant.builder().id(1L).name("Acme").tenantId("acme").build();
    }

    private Cluster testCluster() {
        return Cluster.builder()
                .id(1L).tenant(testTenant()).clusterId("cl-001").name("Production")
                .status("ACTIVE").otpValidated(true).build();
    }

    @Test
    void listClusters_authenticated() throws Exception {
        when(clusterService.getClusters()).thenReturn(List.of(testCluster()));

        mockMvc.perform(get("/api/v1/clusters")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clusterId").value("cl-001"))
                .andExpect(jsonPath("$[0].name").value("Production"));
    }

    @Test
    void listClusters_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/clusters"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void attachCluster_success() throws Exception {
        Cluster cluster = testCluster();
        ClusterOtp otp = ClusterOtp.builder()
                .id(1L).cluster(cluster).otpCode("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        when(clusterService.attachCluster("new-cluster", "New Cluster")).thenReturn(otp);

        AttachClusterRequest request = new AttachClusterRequest();
        request.setClusterId("new-cluster");
        request.setName("New Cluster");

        mockMvc.perform(post("/api/v1/clusters/attach")
                        .with(TestHelper.adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpCode").value("123456"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void attachCluster_missingClusterId_returns400() throws Exception {
        AttachClusterRequest request = new AttachClusterRequest();

        mockMvc.perform(post("/api/v1/clusters/attach")
                        .with(TestHelper.adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateOtp_success() throws Exception {
        when(clusterService.validateOtp("cl-001", "123456")).thenReturn(true);

        mockMvc.perform(post("/api/v1/clusters/validate-otp")
                        .header("X-ODPSC-Cluster-ID", "cl-001")
                        .header("X-ODPSC-Attachment-OTP", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("validated"));
    }

    @Test
    void validateOtp_invalid_returns400() throws Exception {
        when(clusterService.validateOtp("cl-001", "000000")).thenReturn(false);

        mockMvc.perform(post("/api/v1/clusters/validate-otp")
                        .header("X-ODPSC-Cluster-ID", "cl-001")
                        .header("X-ODPSC-Attachment-OTP", "000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void detachCluster_success() throws Exception {
        mockMvc.perform(post("/api/v1/clusters/1/detach")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("detached"));

        verify(clusterService).detach(1L);
    }

    @Test
    void deleteCluster_success() throws Exception {
        mockMvc.perform(delete("/api/v1/clusters/1")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));

        verify(clusterService).delete(1L);
    }

    @Test
    void getCluster_found() throws Exception {
        when(clusterService.findById(1L)).thenReturn(Optional.of(testCluster()));

        mockMvc.perform(get("/api/v1/clusters/1")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterId").value("cl-001"));
    }

    @Test
    void getCluster_notFound() throws Exception {
        when(clusterService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/clusters/999")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getClusterBundles_success() throws Exception {
        when(bundleService.getBundlesForCluster(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/clusters/1/bundles")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- Multi-realm / operator access tests ---

    @Test
    void listClusters_operator_success() throws Exception {
        when(clusterService.getClusters()).thenReturn(List.of(testCluster()));

        mockMvc.perform(get("/api/v1/clusters")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clusterId").value("cl-001"));
    }

    @Test
    void getCluster_operator_success() throws Exception {
        when(clusterService.findById(1L)).thenReturn(Optional.of(testCluster()));

        mockMvc.perform(get("/api/v1/clusters/1")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Production"));
    }

    @Test
    void getClusterBundles_operator_success() throws Exception {
        when(bundleService.getBundlesForCluster(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/clusters/1/bundles")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
