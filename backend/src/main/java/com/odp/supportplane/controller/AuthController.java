package com.odp.supportplane.controller;

import com.odp.supportplane.dto.request.LoginRequest;
import com.odp.supportplane.dto.request.RegisterRequest;
import com.odp.supportplane.dto.response.AuthResponse;
import com.odp.supportplane.dto.response.TenantResponse;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.model.User;
import com.odp.supportplane.repository.UserRepository;
import com.odp.supportplane.service.KeycloakService;
import com.odp.supportplane.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TenantService tenantService;
    private final KeycloakService keycloakService;
    private final UserRepository userRepository;

    @Value("${keycloak.clients-realm}")
    private String clientsRealm;

    @Value("${keycloak.support-realm}")
    private String supportRealm;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String slug = tenantService.generateSlug(request.getCompanyName());

        if (tenantService.existsByTenantId(slug)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Company already registered"));
        }

        Tenant tenant = tenantService.create(request.getCompanyName(), slug);

        String keycloakId = keycloakService.createUser(
                clientsRealm, request.getEmail(), request.getPassword(),
                request.getFullName(), slug, List.of("ADMIN"));

        User user = User.builder()
                .tenant(tenant)
                .keycloakId(keycloakId)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .role("ADMIN")
                .build();
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "tenantId", slug,
                "message", "Registration successful. Use tenant ID '" + slug + "' to login."
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String realm = "support".equals(request.getTenantId()) ? supportRealm : clientsRealm;

        try {
            Map<String, Object> tokens = keycloakService.login(
                    realm, request.getUsername(), request.getPassword());

            AuthResponse response = new AuthResponse(
                    (String) tokens.get("access_token"),
                    (String) tokens.get("refresh_token"),
                    "Bearer",
                    tokens.get("expires_in") instanceof Number n ? n.longValue() : 300,
                    request.getTenantId()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Authentication failed"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        String tenantId = body.get("tenantId");
        if (refreshToken == null || tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing refreshToken or tenantId"));
        }

        String realm = "support".equals(tenantId) ? supportRealm : clientsRealm;

        try {
            Map<String, Object> tokens = keycloakService.refreshToken(realm, refreshToken);
            AuthResponse response = new AuthResponse(
                    (String) tokens.get("access_token"),
                    (String) tokens.get("refresh_token"),
                    "Bearer",
                    tokens.get("expires_in") instanceof Number n ? n.longValue() : 300,
                    tenantId
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Token refresh failed"));
        }
    }
}
