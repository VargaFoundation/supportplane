package varga.supportplane;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class TestHelper {

    private TestHelper() {}

    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor tenantJwt(String tenantId, String role) {
        return jwt().jwt(builder -> builder
                .claim("sub", "test-user")
                .claim("tenant_id", tenantId)
                .claim("iss", "http://localhost:8080/realms/clients")
                .claim("realm_access", Map.of("roles", List.of(role)))
        );
    }

    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor adminJwt() {
        return tenantJwt("test-tenant", "ADMIN");
    }

    public static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor operatorJwt() {
        return jwt().jwt(builder -> builder
                .claim("sub", "operator-user")
                .claim("tenant_id", "support")
                .claim("iss", "http://localhost:8080/realms/support")
                .claim("realm_access", Map.of("roles", List.of("OPERATOR")))
        );
    }
}
