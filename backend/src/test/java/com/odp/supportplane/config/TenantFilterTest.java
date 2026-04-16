package com.odp.supportplane.config;

import com.odp.supportplane.TestHelper;
import com.odp.supportplane.controller.DashboardController;
import com.odp.supportplane.dto.response.DashboardResponse;
import com.odp.supportplane.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

/**
 * Tests for TenantFilter: verifies that JWT claims from both Keycloak realms
 * (clients and support) are correctly extracted into TenantContext.
 *
 * Uses DashboardController as the target endpoint since DashboardService
 * reads from TenantContext (isOperator, getTenantId) to decide the response shape.
 */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, TenantFilter.class})
class TenantFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void clientsRealm_setsCorrectTenantContext() throws Exception {
        // The DashboardService.getDashboard() is called after TenantFilter sets context.
        // We capture the call to verify TenantContext was set correctly.
        when(dashboardService.getDashboard()).thenAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("acme-corp");
            assertThat(TenantContext.getRealm()).isEqualTo("clients");
            assertThat(TenantContext.getRole()).isEqualTo("ADMIN");
            assertThat(TenantContext.isOperator()).isFalse();
            return DashboardResponse.builder().totalClusters(1).build();
        });

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(TestHelper.tenantJwt("acme-corp", "ADMIN")))
                .andExpect(status().isOk());

        verify(dashboardService).getDashboard();
    }

    @Test
    void supportRealm_setsOperatorContext() throws Exception {
        when(dashboardService.getDashboard()).thenAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("support");
            assertThat(TenantContext.getRealm()).isEqualTo("support");
            assertThat(TenantContext.getRole()).isEqualTo("OPERATOR");
            assertThat(TenantContext.isOperator()).isTrue();
            return DashboardResponse.builder().totalClusters(10).totalTenants(3).build();
        });

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk());

        verify(dashboardService).getDashboard();
    }

    @Test
    void userRole_setsUserContext() throws Exception {
        when(dashboardService.getDashboard()).thenAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("acme-corp");
            assertThat(TenantContext.getRole()).isEqualTo("USER");
            assertThat(TenantContext.isOperator()).isFalse();
            return DashboardResponse.builder().totalClusters(1).build();
        });

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(TestHelper.tenantJwt("acme-corp", "USER")))
                .andExpect(status().isOk());

        verify(dashboardService).getDashboard();
    }

    @Test
    void contextIsClearedAfterRequest() throws Exception {
        when(dashboardService.getDashboard()).thenReturn(
                DashboardResponse.builder().totalClusters(1).build());

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk());

        // After the request completes, TenantContext should be cleared
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getRealm()).isNull();
        assertThat(TenantContext.getRole()).isNull();
    }

    @Test
    void unauthenticated_doesNotSetContext() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized());

        // No context should be set for unauthenticated requests
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void jwtWithMultipleRoles_selectsHighestPriority() throws Exception {
        // JWT with both ADMIN and USER roles - ADMIN should win
        when(dashboardService.getDashboard()).thenAnswer(invocation -> {
            assertThat(TenantContext.getRole()).isEqualTo("ADMIN");
            return DashboardResponse.builder().totalClusters(1).build();
        });

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(jwt().jwt(builder -> builder
                                .claim("sub", "multi-role-user")
                                .claim("tenant_id", "acme")
                                .claim("iss", "http://localhost:8080/realms/clients")
                                .claim("realm_access", Map.of("roles", List.of("USER", "ADMIN"))))))
                .andExpect(status().isOk());

        verify(dashboardService).getDashboard();
    }

    @Test
    void operatorRole_takesPrecedenceOverOtherRoles() throws Exception {
        when(dashboardService.getDashboard()).thenAnswer(invocation -> {
            assertThat(TenantContext.getRole()).isEqualTo("OPERATOR");
            assertThat(TenantContext.isOperator()).isTrue();
            return DashboardResponse.builder().totalClusters(10).build();
        });

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(jwt().jwt(builder -> builder
                                .claim("sub", "super-user")
                                .claim("tenant_id", "support")
                                .claim("iss", "http://localhost:8080/realms/support")
                                .claim("realm_access", Map.of("roles", List.of("OPERATOR", "ADMIN"))))))
                .andExpect(status().isOk());

        verify(dashboardService).getDashboard();
    }
}
