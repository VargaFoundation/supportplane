package com.odp.supportplane.controller;

import com.odp.supportplane.TestHelper;
import com.odp.supportplane.config.SecurityConfig;
import com.odp.supportplane.config.TenantFilter;
import com.odp.supportplane.dto.response.DashboardResponse;
import com.odp.supportplane.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, TenantFilter.class})
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void getDashboard_authenticated() throws Exception {
        DashboardResponse response = DashboardResponse.builder()
                .totalClusters(5).activeClusters(3).totalBundles(10)
                .openTickets(2).totalUsers(8).build();
        when(dashboardService.getDashboard()).thenReturn(response);

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClusters").value(5))
                .andExpect(jsonPath("$.activeClusters").value(3))
                .andExpect(jsonPath("$.openTickets").value(2));
    }

    @Test
    void getDashboard_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDashboard_operator() throws Exception {
        DashboardResponse response = DashboardResponse.builder()
                .totalClusters(20).activeClusters(15).totalBundles(50)
                .openTickets(8).pendingRecommendations(3)
                .totalUsers(30).totalTenants(5).build();
        when(dashboardService.getDashboard()).thenReturn(response);

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTenants").value(5))
                .andExpect(jsonPath("$.pendingRecommendations").value(3));
    }
}
