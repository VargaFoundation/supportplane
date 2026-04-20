package varga.supportplane.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();

                // Extract tenant_id from JWT claims
                String tenantId = jwt.getClaimAsString("tenant_id");
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }

                // Extract realm from issuer
                String issuer = jwt.getClaimAsString("iss");
                if (issuer != null) {
                    String realm = issuer.substring(issuer.lastIndexOf('/') + 1);
                    TenantContext.setRealm(realm);
                }

                // Extract role from realm_access.roles
                Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                if (realmAccess != null) {
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) realmAccess.get("roles");
                    if (roles != null) {
                        if (roles.contains("OPERATOR")) {
                            TenantContext.setRole("OPERATOR");
                        } else if (roles.contains("ADMIN")) {
                            TenantContext.setRole("ADMIN");
                        } else {
                            TenantContext.setRole("USER");
                        }
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
