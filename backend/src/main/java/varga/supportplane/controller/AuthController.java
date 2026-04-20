package varga.supportplane.controller;

import varga.supportplane.dto.request.LoginRequest;
import varga.supportplane.dto.request.RegisterRequest;
import varga.supportplane.dto.response.AuthResponse;
import varga.supportplane.dto.response.TenantResponse;
import varga.supportplane.model.Tenant;
import varga.supportplane.model.User;
import varga.supportplane.repository.UserRepository;
import varga.supportplane.service.KeycloakService;
import varga.supportplane.service.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private static final ObjectMapper JWT_MAPPER = new ObjectMapper();

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

            String accessToken = (String) tokens.get("access_token");
            AuthResponse response = new AuthResponse(
                    accessToken,
                    (String) tokens.get("refresh_token"),
                    "Bearer",
                    tokens.get("expires_in") instanceof Number n ? n.longValue() : 300,
                    request.getTenantId(),
                    extractRole(accessToken)
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
            String accessToken = (String) tokens.get("access_token");
            AuthResponse response = new AuthResponse(
                    accessToken,
                    (String) tokens.get("refresh_token"),
                    "Bearer",
                    tokens.get("expires_in") instanceof Number n ? n.longValue() : 300,
                    tenantId,
                    extractRole(accessToken)
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Token refresh failed"));
        }
    }

    private String extractRole(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return "USER";
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> claims = JWT_MAPPER.readValue(new String(payload, StandardCharsets.UTF_8), Map.class);
            Object realmAccess = claims.get("realm_access");
            if (realmAccess instanceof Map<?, ?> ra && ra.get("roles") instanceof List<?> roles) {
                if (roles.contains("OPERATOR")) return "OPERATOR";
                if (roles.contains("ADMIN")) return "ADMIN";
            }
            return "USER";
        } catch (Exception e) {
            log.warn("Failed to extract role from JWT: {}", e.getMessage());
            return "USER";
        }
    }
}
