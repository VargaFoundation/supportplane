package com.odp.supportplane.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class KeycloakService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.clients-realm}")
    private String clientsRealm;

    @Value("${keycloak.support-realm}")
    private String supportRealm;

    @Value("${keycloak.admin-client-id}")
    private String adminClientId;

    @Value("${keycloak.admin-username}")
    private String adminUsername;

    @Value("${keycloak.admin-password}")
    private String adminPassword;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getAdminToken() {
        String url = authServerUrl + "/realms/master/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", adminClientId);
        form.add("username", adminUsername);
        form.add("password", adminPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> response = restTemplate.postForEntity(url,
                new HttpEntity<>(form, headers), Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return (String) response.getBody().get("access_token");
        }
        throw new RuntimeException("Failed to get Keycloak admin token");
    }

    public String createUser(String realm, String email, String password, String fullName,
                              String tenantId, List<String> roles) {
        String adminToken = getAdminToken();
        String url = authServerUrl + "/admin/realms/" + realm + "/users";

        Map<String, Object> userRep = new HashMap<>();
        userRep.put("username", email);
        userRep.put("email", email);
        userRep.put("firstName", fullName.split(" ")[0]);
        userRep.put("lastName", fullName.contains(" ") ? fullName.substring(fullName.indexOf(' ') + 1) : "");
        userRep.put("enabled", true);
        userRep.put("emailVerified", true);

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("tenant_id", List.of(tenantId));
        userRep.put("attributes", attributes);

        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "password");
        credential.put("value", password);
        credential.put("temporary", false);
        userRep.put("credentials", List.of(credential));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        ResponseEntity<Void> response = restTemplate.postForEntity(url,
                new HttpEntity<>(userRep, headers), Void.class);

        if (response.getStatusCode() == HttpStatus.CREATED) {
            String location = response.getHeaders().getFirst("Location");
            if (location != null) {
                String userId = location.substring(location.lastIndexOf('/') + 1);
                assignRealmRoles(realm, userId, roles, adminToken);
                return userId;
            }
        }

        log.warn("Keycloak user creation returned status: {}", response.getStatusCode());
        return null;
    }

    private void assignRealmRoles(String realm, String userId, List<String> roles, String adminToken) {
        try {
            String rolesUrl = authServerUrl + "/admin/realms/" + realm + "/roles";
            String assignUrl = authServerUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(adminToken);

            List<Map<String, Object>> roleRepresentations = new java.util.ArrayList<>();
            for (String roleName : roles) {
                ResponseEntity<Map> roleResponse = restTemplate.exchange(
                        rolesUrl + "/" + roleName, HttpMethod.GET,
                        new HttpEntity<>(headers), Map.class);
                if (roleResponse.getStatusCode().is2xxSuccessful() && roleResponse.getBody() != null) {
                    roleRepresentations.add(roleResponse.getBody());
                }
            }

            if (!roleRepresentations.isEmpty()) {
                restTemplate.postForEntity(assignUrl,
                        new HttpEntity<>(roleRepresentations, headers), Void.class);
            }
        } catch (Exception e) {
            log.warn("Failed to assign realm roles to user {}: {}", userId, e.getMessage());
        }
    }

    public Map<String, Object> login(String realm, String username, String password) {
        String url = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "supportplane-frontend");
        form.add("username", username);
        form.add("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> response = restTemplate.postForEntity(url,
                new HttpEntity<>(form, headers), Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        }
        throw new RuntimeException("Authentication failed");
    }
}
