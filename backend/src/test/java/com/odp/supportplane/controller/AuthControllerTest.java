package com.odp.supportplane.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odp.supportplane.config.SecurityConfig;
import com.odp.supportplane.config.TenantFilter;
import com.odp.supportplane.dto.request.LoginRequest;
import com.odp.supportplane.dto.request.RegisterRequest;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.model.User;
import com.odp.supportplane.repository.UserRepository;
import com.odp.supportplane.service.KeycloakService;
import com.odp.supportplane.service.TenantService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, TenantFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TenantService tenantService;

    @MockBean
    private KeycloakService keycloakService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void register_success() throws Exception {
        when(tenantService.generateSlug("Acme Corp")).thenReturn("acme-corp");
        when(tenantService.existsByTenantId("acme-corp")).thenReturn(false);
        when(tenantService.create("Acme Corp", "acme-corp")).thenReturn(
                Tenant.builder().id(1L).name("Acme Corp").tenantId("acme-corp").build());
        when(keycloakService.createUser(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("kc-user-id");
        when(userRepository.save(any(User.class))).thenReturn(User.builder().id(1L).build());

        RegisterRequest request = new RegisterRequest();
        request.setCompanyName("Acme Corp");
        request.setEmail("admin@acme.com");
        request.setFullName("John Doe");
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("acme-corp"));
    }

    @Test
    void register_duplicateCompany_returns400() throws Exception {
        when(tenantService.generateSlug("Acme Corp")).thenReturn("acme-corp");
        when(tenantService.existsByTenantId("acme-corp")).thenReturn(true);

        RegisterRequest request = new RegisterRequest();
        request.setCompanyName("Acme Corp");
        request.setEmail("admin@acme.com");
        request.setFullName("John Doe");
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Company already registered"));
    }

    @Test
    void register_validationError_missingFields() throws Exception {
        RegisterRequest request = new RegisterRequest();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields").isNotEmpty());
    }

    @Test
    void login_success() throws Exception {
        when(keycloakService.login(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "access_token", "test-token",
                        "refresh_token", "test-refresh",
                        "expires_in", 300
                ));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin@acme.com");
        request.setPassword("password123");
        request.setTenantId("acme-corp");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-token"))
                .andExpect(jsonPath("$.refreshToken").value("test-refresh"))
                .andExpect(jsonPath("$.tenantId").value("acme-corp"));
    }

    @Test
    void login_failure_returns401() throws Exception {
        when(keycloakService.login(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Authentication failed"));

        LoginRequest request = new LoginRequest();
        request.setUsername("wrong@acme.com");
        request.setPassword("wrong");
        request.setTenantId("acme-corp");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication failed"));
    }

    @Test
    void login_supportTenant_usesSupportRealm() throws Exception {
        when(keycloakService.login(eq("support"), anyString(), anyString()))
                .thenReturn(Map.of(
                        "access_token", "operator-token",
                        "refresh_token", "op-refresh",
                        "expires_in", 300
                ));

        LoginRequest request = new LoginRequest();
        request.setUsername("operator");
        request.setPassword("password");
        request.setTenantId("support");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("support"));
    }
}
