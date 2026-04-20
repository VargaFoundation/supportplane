package varga.supportplane.service;

import varga.supportplane.config.AccessControl;
import varga.supportplane.config.TenantContext;
import varga.supportplane.model.Tenant;
import varga.supportplane.model.User;
import varga.supportplane.repository.LicenseRepository;
import varga.supportplane.repository.TenantRepository;
import varga.supportplane.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final KeycloakService keycloakService;
    private final LicenseRepository licenseRepository;
    private final AuditService auditService;

    public List<User> getUsers() {
        if (TenantContext.isOperator()) {
            return userRepository.findAll();
        }
        Tenant tenant = getCurrentTenant();
        return userRepository.findByTenantId(tenant.getId());
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    @Transactional
    public User create(String email, String fullName, String password, String role, String targetTenantId) {
        AccessControl.requireAdminOrOperator();

        Tenant tenant;
        if (TenantContext.isOperator() && targetTenantId != null && !targetTenantId.isBlank()) {
            tenant = tenantRepository.findByTenantId(targetTenantId)
                    .orElseThrow(() -> new RuntimeException("Tenant not found: " + targetTenantId));
        } else {
            tenant = getCurrentTenant();
        }

        // Enforce license limit
        licenseRepository.findByTenantId(tenant.getId()).ifPresent(license -> {
            long currentCount = userRepository.countByTenantIdAndActiveTrue(tenant.getId());
            if (license.getMaxUsers() != null && currentCount >= license.getMaxUsers()) {
                throw new RuntimeException("License limit reached: maximum " + license.getMaxUsers() + " users allowed");
            }
        });

        // Tenant users always go to the clients realm, regardless of who creates them
        String realm = "clients";

        String keycloakId = keycloakService.createUser(
                realm, email, password, fullName,
                tenant.getTenantId(), List.of(role != null ? role : "USER"));

        User user = User.builder()
                .tenant(tenant)
                .keycloakId(keycloakId)
                .email(email)
                .fullName(fullName)
                .role(role != null ? role : "USER")
                .build();
        user = userRepository.save(user);
        auditService.logForTenant(tenant, "USER_CREATED", "USER", String.valueOf(user.getId()), email);
        return user;
    }

    @Transactional
    public void delete(Long id) {
        AccessControl.requireAdminOrOperator();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify caller has access to this user's tenant
        if (!TenantContext.isOperator()) {
            Tenant callerTenant = getCurrentTenant();
            if (!user.getTenant().getId().equals(callerTenant.getId())) {
                throw new RuntimeException("User not found");
            }
        }

        user.setActive(false);
        userRepository.save(user);
        auditService.logForTenant(user.getTenant(), "USER_DEACTIVATED", "USER", String.valueOf(id), user.getEmail());

        // Disable in Keycloak
        if (user.getKeycloakId() != null) {
            try {
                String realm = "clients";
                keycloakService.disableUser(realm, user.getKeycloakId());
            } catch (Exception e) {
                // Non-critical: user is already deactivated in DB
            }
        }
    }

    @Transactional
    public User update(Long id, String fullName, String role, Boolean active) {
        AccessControl.requireAdminOrOperator();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (fullName != null) user.setFullName(fullName);
        if (role != null) user.setRole(role);
        if (active != null) user.setActive(active);

        return userRepository.save(user);
    }

    private Tenant getCurrentTenant() {
        String tenantId = TenantContext.getTenantId();
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
    }
}
