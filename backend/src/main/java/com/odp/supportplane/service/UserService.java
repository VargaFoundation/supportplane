package com.odp.supportplane.service;

import com.odp.supportplane.config.TenantContext;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.model.User;
import com.odp.supportplane.repository.TenantRepository;
import com.odp.supportplane.repository.UserRepository;
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
    public User create(String email, String fullName, String password, String role) {
        Tenant tenant = getCurrentTenant();
        String realm = TenantContext.isOperator() ? "support" : "clients";

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
        return userRepository.save(user);
    }

    @Transactional
    public User update(Long id, String fullName, String role, Boolean active) {
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
