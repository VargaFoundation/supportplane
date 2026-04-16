package com.odp.supportplane.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odp.supportplane.TestHelper;
import com.odp.supportplane.config.SecurityConfig;
import com.odp.supportplane.config.TenantFilter;
import com.odp.supportplane.model.RecommendationRule;
import com.odp.supportplane.service.RecommendationRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecommendationRuleController.class)
@Import({SecurityConfig.class, TenantFilter.class})
class RecommendationRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RecommendationRuleService ruleService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private RecommendationRule testRule() {
        return RecommendationRule.builder()
                .id(1L)
                .code("SEC-AUTH-KERBEROS")
                .title("Kerberos authentication")
                .category("Security")
                .subcategory("Authentication")
                .component("Kerberos")
                .defaultLikelihood("MEDIUM")
                .defaultSeverity("CRITICAL")
                .enabled(true)
                .build();
    }

    @Test
    void listRules_authenticated() throws Exception {
        when(ruleService.getAll()).thenReturn(List.of(testRule()));

        mockMvc.perform(get("/api/v1/recommendation-rules")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SEC-AUTH-KERBEROS"))
                .andExpect(jsonPath("$[0].category").value("Security"));
    }

    @Test
    void listRules_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/recommendation-rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRules_filterByCategory() throws Exception {
        when(ruleService.getByCategory("Security")).thenReturn(List.of(testRule()));

        mockMvc.perform(get("/api/v1/recommendation-rules")
                        .param("category", "Security")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("Security"));
    }

    @Test
    void listRules_filterByComponent() throws Exception {
        when(ruleService.getByComponent("Kerberos")).thenReturn(List.of(testRule()));

        mockMvc.perform(get("/api/v1/recommendation-rules")
                        .param("component", "Kerberos")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].component").value("Kerberos"));
    }

    @Test
    void getRule_found() throws Exception {
        when(ruleService.findById(1L)).thenReturn(Optional.of(testRule()));

        mockMvc.perform(get("/api/v1/recommendation-rules/1")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SEC-AUTH-KERBEROS"));
    }

    @Test
    void getRule_notFound() throws Exception {
        when(ruleService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/recommendation-rules/999")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRule_success() throws Exception {
        when(ruleService.create(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(testRule());

        Map<String, Object> body = Map.of(
                "code", "SEC-AUTH-KERBEROS",
                "title", "Kerberos authentication",
                "category", "Security",
                "component", "Kerberos"
        );

        mockMvc.perform(post("/api/v1/recommendation-rules")
                        .with(TestHelper.operatorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SEC-AUTH-KERBEROS"));
    }

    @Test
    void deleteRule_success() throws Exception {
        mockMvc.perform(delete("/api/v1/recommendation-rules/1")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isNoContent());

        verify(ruleService).delete(1L);
    }

    @Test
    void toggleRule_success() throws Exception {
        RecommendationRule toggled = testRule();
        toggled.setEnabled(false);
        when(ruleService.toggleEnabled(1L)).thenReturn(toggled);

        mockMvc.perform(put("/api/v1/recommendation-rules/1/toggle")
                        .with(TestHelper.operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }
}
