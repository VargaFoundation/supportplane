package varga.supportplane.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import varga.supportplane.model.Tenant;
import varga.supportplane.service.TenantService;
import varga.supportplane.service.TenantStreamingService;
import varga.supportplane.service.TenantStreamingService.StreamingCredentials;

/**
 * Operator endpoints to manage per-tenant Pulsar streaming credentials.
 * See ADR 0003 for the security architecture.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/streaming")
@RequiredArgsConstructor
public class TenantStreamingController {

    private final TenantService tenantService;
    private final TenantStreamingService streamingService;

    @Value("${pulsar.service-url:}")
    private String serviceUrl;

    @PostMapping("/enable")
    public ResponseEntity<StreamingCredentialsResponse> enable(@PathVariable String tenantId) {
        Tenant tenant = tenantService.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        StreamingCredentials creds = streamingService.enableStreaming(tenant);
        return ResponseEntity.ok(StreamingCredentialsResponse.from(creds, serviceUrl));
    }

    @PostMapping("/rotate-token")
    public ResponseEntity<StreamingCredentialsResponse> rotate(@PathVariable String tenantId) {
        Tenant tenant = tenantService.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        StreamingCredentials creds = streamingService.rotateToken(tenant);
        return ResponseEntity.ok(StreamingCredentialsResponse.from(creds, serviceUrl));
    }

    public record StreamingCredentialsResponse(
            String serviceUrl,
            String namespace,
            String role,
            String token
    ) {
        static StreamingCredentialsResponse from(StreamingCredentials c, String url) {
            return new StreamingCredentialsResponse(url, c.namespace(), c.role(), c.token());
        }
    }
}
