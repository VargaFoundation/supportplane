package varga.supportplane.controller;

import varga.supportplane.TestHelper;
import varga.supportplane.config.SecurityConfig;
import varga.supportplane.config.TenantFilter;
import varga.supportplane.model.Bundle;
import varga.supportplane.service.BundleService;
import varga.supportplane.service.ClusterService;
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
import static org.mockito.Mockito.verify;
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
        when(bundleService.receiveBundle(any(), eq("b-001"), eq("cl-001"), isNull(), any()))
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

    @Test
    void uploadBundle_withOtp_validatesAndReceives() throws Exception {
        when(clusterService.validateOtp("cl-001", "123456")).thenReturn(true);
        Bundle bundle = Bundle.builder()
                .id(1L).bundleId("b-002").filename("bundle.zip").sizeBytes(2048L).build();
        when(bundleService.receiveBundle(any(), eq("b-002"), eq("cl-001"), eq("123456"), any()))
                .thenReturn(bundle);

        MockMultipartFile file = new MockMultipartFile(
                "bundle", "bundle.zip", "application/zip", "test-data".getBytes());

        mockMvc.perform(multipart("/api/v1/bundles/upload")
                        .file(file)
                        .header("X-ODPSC-Bundle-ID", "b-002")
                        .header("X-ODPSC-Cluster-ID", "cl-001")
                        .header("X-ODPSC-Attachment-OTP", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"))
                .andExpect(jsonPath("$.bundle_id").value("b-002"));

        verify(clusterService).validateOtp("cl-001", "123456");
    }

    @Test
    void uploadBundle_noAuth_stillWorks() throws Exception {
        // Bundle upload is a public endpoint (no JWT required)
        Bundle bundle = Bundle.builder()
                .id(1L).bundleId("b-003").filename("bundle.zip").sizeBytes(512L).build();
        when(bundleService.receiveBundle(any(), eq("b-003"), eq("cl-001"), isNull(), any()))
                .thenReturn(bundle);

        MockMultipartFile file = new MockMultipartFile(
                "bundle", "bundle.zip", "application/zip", "data".getBytes());

        mockMvc.perform(multipart("/api/v1/bundles/upload")
                        .file(file)
                        .header("X-ODPSC-Bundle-ID", "b-003")
                        .header("X-ODPSC-Cluster-ID", "cl-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));
    }

    @Test
    void getBundle_operator_success() throws Exception {
        Bundle bundle = Bundle.builder()
                .id(1L).bundleId("b-001").filename("bundle.zip").sizeBytes(1024L).build();
        when(bundleService.findByBundleId("b-001")).thenReturn(Optional.of(bundle));

        mockMvc.perform(get("/api/v1/bundles/b-001")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundleId").value("b-001"));
    }
}
