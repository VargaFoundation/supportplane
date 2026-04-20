package varga.supportplane.config;

import org.springframework.security.access.AccessDeniedException;

/**
 * Static authorization helpers that read from TenantContext (set by TenantFilter).
 * Throw AccessDeniedException (403) on violation.
 */
public final class AccessControl {

    private AccessControl() {}

    public static void requireOperator() {
        if (!TenantContext.isOperator()) {
            throw new AccessDeniedException("Operator access required");
        }
    }

    public static void requireAdminOrOperator() {
        String role = TenantContext.getRole();
        if (!"ADMIN".equals(role) && !"OPERATOR".equals(role)) {
            throw new AccessDeniedException("Admin or operator access required");
        }
    }

    /**
     * Verify the caller has access to a resource belonging to the given tenant ID.
     * Operators have cross-tenant access. Tenant users can only access their own tenant's resources.
     */
    public static void requireTenantAccess(String resourceTenantSlug) {
        if (TenantContext.isOperator()) return;
        String callerTenant = TenantContext.getTenantId();
        if (callerTenant == null || !callerTenant.equals(resourceTenantSlug)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Verify the caller has access to a resource belonging to the given tenant DB ID.
     * Operators have cross-tenant access.
     */
    public static void requireTenantAccessById(Long resourceTenantId, Long callerTenantId) {
        if (TenantContext.isOperator()) return;
        if (callerTenantId == null || !callerTenantId.equals(resourceTenantId)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
