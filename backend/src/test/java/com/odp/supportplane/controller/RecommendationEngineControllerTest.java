package com.odp.supportplane.controller;

import com.odp.supportplane.TestHelper;
import com.odp.supportplane.config.SecurityConfig;
import com.odp.supportplane.config.TenantFilter;
import com.odp.supportplane.dto.response.AuditReportResponse;
import com.odp.supportplane.dto.response.ComponentSummaryResponse;
import com.odp.supportplane.model.AuditRun;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.service.RecommendationEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecommendationEngineController.class)
@Import({SecurityConfig.class, TenantFilter.class})
class RecommendationEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationEngineService engineService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private AuditRun testAuditRun() {
        Tenant tenant = Tenant.builder().id(1L).name("Acme").tenantId("acme").build();
        Cluster cluster = Cluster.builder().id(1L).tenant(tenant).clusterId("cl-001").name("Prod").build();
        return AuditRun.builder()
                .id(1L)
                .cluster(cluster)
                .status("COMPLETED")
                .rulesEvaluated(25)
                .findingsCount(25)
                .summary(Map.of("OK", 5, "WARNING", 8, "CRITICAL", 10, "UNKNOWN", 2))
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void evaluateCluster_success() throws Exception {
        when(engineService.evaluateCluster(eq(1L), isNull())).thenReturn(testAuditRun());

        mockMvc.perform(post("/api/v1/clusters/1/evaluate")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rulesEvaluated").value(25))
                .andExpect(jsonPath("$.findingsCount").value(25));
    }

    @Test
    void evaluateCluster_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/clusters/1/evaluate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAuditReport_success() throws Exception {
        AuditReportResponse report = new AuditReportResponse();
        report.setClusterName("Prod");
        report.setClusterId("cl-001");
        report.setGeneratedAt(LocalDateTime.now());
        report.setSummary(Map.of("total", 5, "OK", 2, "CRITICAL", 3));
        report.setCategories(List.of());

        when(engineService.getAuditReport(1L)).thenReturn(report);

        mockMvc.perform(get("/api/v1/clusters/1/audit-report")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterName").value("Prod"))
                .andExpect(jsonPath("$.clusterId").value("cl-001"));
    }

    @Test
    void getComponentSummary_success() throws Exception {
        when(engineService.getComponentSummary(1L)).thenReturn(List.of(
                new ComponentSummaryResponse("HDFS", 1, 1, 2, 0),
                new ComponentSummaryResponse("Yarn", 0, 2, 1, 0)
        ));

        mockMvc.perform(get("/api/v1/clusters/1/component-summary")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].component").value("HDFS"))
                .andExpect(jsonPath("$[0].criticalCount").value(2))
                .andExpect(jsonPath("$[1].component").value("Yarn"));
    }

    @Test
    void getAuditRuns_success() throws Exception {
        when(engineService.getAuditRuns(1L)).thenReturn(List.of(testAuditRun()));

        mockMvc.perform(get("/api/v1/clusters/1/audit-runs")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }
}
