package com.odp.supportplane.controller;

import com.odp.supportplane.TestHelper;
import com.odp.supportplane.config.SecurityConfig;
import com.odp.supportplane.config.TenantFilter;
import com.odp.supportplane.model.Bundle;
import com.odp.supportplane.service.BundleService;
import com.odp.supportplane.service.ClusterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BundleController.class)
@Import({SecurityConfig.class, TenantFilter.class})
class BundleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BundleService bundleService;

    @MockBean
    private ClusterService clusterService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void uploadBundle_success() throws Exception {
        Bundle bundle = Bundle.builder()
                .id(1L).bundleId("b-001").filename("bundle.zip").sizeBytes(1024L).build();
        when(bundleService.receiveBundle(any(), eq("b-001"), eq("cl-001"), isNull()))
                .thenReturn(bundle);

        MockMultipartFile file = new MockMultipartFile(
                "bundle", "bundle.zip", "application/zip", "test-data".getBytes());

        mockMvc.perform(multipart("/api/v1/bundles/upload")
                        .file(file)
                        .header("X-ODPSC-Bundle-ID", "b-001")
                        .header("X-ODPSC-Cluster-ID", "cl-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"))
                .andExpect(jsonPath("$.bundle_id").value("b-001"));
    }

    @Test
    void uploadBundle_missingBundleId_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "bundle", "bundle.zip", "application/zip", "test-data".getBytes());

        mockMvc.perform(multipart("/api/v1/bundles/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing X-ODPSC-Bundle-ID header"));
    }

    @Test
    void getBundle_found() throws Exception {
        Bundle bundle = Bundle.builder()
                .id(1L).bundleId("b-001").filename("bundle.zip").sizeBytes(1024L).build();
        when(bundleService.findByBundleId("b-001")).thenReturn(Optional.of(bundle));

        mockMvc.perform(get("/api/v1/bundles/b-001")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundleId").value("b-001"));
    }

    @Test
    void getBundle_notFound() throws Exception {
        when(bundleService.findByBundleId("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/bundles/nonexistent")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isNotFound());
    }
}
