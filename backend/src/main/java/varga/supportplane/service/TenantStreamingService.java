package varga.supportplane.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminBuilder;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.AuthenticationFactory;
import org.apache.pulsar.common.policies.data.AuthAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import varga.supportplane.config.AccessControl;
import varga.supportplane.model.Tenant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

/**
 * Provisions Pulsar namespaces and issues per-tenant JWT bearer tokens.
 *
 * Flow when an operator activates streaming for a tenant
 * (see TenantStreamingController):
 *   1. Create the namespace {@code supportplane/{tenantId}} via PulsarAdmin
 *      (idempotent — does nothing if it already exists).
 *   2. Grant {@code produce} + {@code consume} on that namespace to the role
 *      {@code tenant-{tenantId}}.
 *   3. Sign a JWT {@code {sub: "tenant-{tenantId}"}} with the backend's
 *      RS256 private key.
 *   4. Return the token + service URL to the caller (UI displays it once;
 *      the customer installs it on the agent host).
 *
 * Rotation is the same flow — re-signing the token does not invalidate the
 * previous one (Pulsar accepts any JWT whose signature matches the
 * configured public key and whose subject has a matching grant). To revoke,
 * remove the grant; to truly invalidate, also rotate the signing keypair.
 *
 * Disabled in dev when {@code pulsar.enabled=false} or no private key is
 * configured — the service is then inert and methods short-circuit.
 *
 * See {@code docs/adr/0003-pulsar-security.md}.
 */
@Service
@Slf4j
public class TenantStreamingService {

    @Value("${pulsar.enabled:false}")
    private boolean enabled;

    @Value("${pulsar.admin-url:http://localhost:8082}")
    private String adminUrl;

    @Value("${pulsar.tenant:supportplane}")
    private String pulsarTenant;

    @Value("${pulsar.auth.token:}")
    private String adminToken;

    @Value("${pulsar.tls.trust-certs-file-path:}")
    private String tlsTrustCertsFilePath;

    @Value("${pulsar.jwt.private-key-path:}")
    private String privateKeyPath;

    @Value("${pulsar.jwt.token-ttl-days:365}")
    private int tokenTtlDays;

    private final AuditService auditService;

    private PulsarAdmin admin;
    private PrivateKey signingKey;

    public TenantStreamingService(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("TenantStreamingService inert — pulsar.enabled=false");
            return;
        }
        try {
            PulsarAdminBuilder b = PulsarAdmin.builder().serviceHttpUrl(adminUrl);
            if (!tlsTrustCertsFilePath.isBlank()) {
                b.tlsTrustCertsFilePath(tlsTrustCertsFilePath)
                        .allowTlsInsecureConnection(false);
            }
            if (!adminToken.isBlank()) {
                b.authentication(AuthenticationFactory.token(adminToken));
            }
            admin = b.build();
            log.info("PulsarAdmin client ready (url={})", adminUrl);
        } catch (Exception e) {
            log.warn("PulsarAdmin init failed: {}", e.getMessage());
        }

        if (privateKeyPath != null && !privateKeyPath.isBlank()) {
            try {
                signingKey = loadPrivateKey(privateKeyPath);
                log.info("JWT signing key loaded from {}", privateKeyPath);
            } catch (Exception e) {
                log.warn("Failed to load JWT signing key from {}: {}",
                        privateKeyPath, e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (admin != null) {
            try { admin.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Provision the Pulsar namespace + ACLs for a tenant and issue its first
     * streaming token. Caller must be an operator.
     */
    public StreamingCredentials enableStreaming(Tenant tenant) {
        AccessControl.requireOperator();
        ensureReady();
        String role = roleFor(tenant);
        String namespace = pulsarTenant + "/" + tenant.getTenantId();

        try {
            ensureTenantExists();
            ensureNamespaceExists(namespace);
            grantNamespacePermission(namespace, role);
        } catch (PulsarAdminException e) {
            throw new IllegalStateException(
                    "Failed to provision Pulsar namespace " + namespace + ": " + e.getMessage(), e);
        }

        String token = mintToken(role);
        auditService.logForTenant(tenant, "STREAMING_ENABLED", "TENANT",
                tenant.getTenantId(), tenant.getName());
        return new StreamingCredentials(namespace, role, token);
    }

    /**
     * Rotate the streaming token for a tenant. The previous token remains
     * valid until it expires or the grant is removed — see ADR 0003 for the
     * full rotation procedure.
     */
    public StreamingCredentials rotateToken(Tenant tenant) {
        AccessControl.requireOperator();
        ensureReady();
        String role = roleFor(tenant);
        String namespace = pulsarTenant + "/" + tenant.getTenantId();
        String token = mintToken(role);
        auditService.logForTenant(tenant, "STREAMING_TOKEN_ROTATED", "TENANT",
                tenant.getTenantId(), tenant.getName());
        return new StreamingCredentials(namespace, role, token);
    }

    private void ensureReady() {
        if (!enabled) {
            throw new IllegalStateException("Streaming feature disabled (pulsar.enabled=false)");
        }
        if (admin == null) {
            throw new IllegalStateException("PulsarAdmin client not initialised");
        }
        if (signingKey == null) {
            throw new IllegalStateException(
                    "JWT signing key unavailable — set pulsar.jwt.private-key-path");
        }
    }

    private void ensureTenantExists() throws PulsarAdminException {
        if (!admin.tenants().getTenants().contains(pulsarTenant)) {
            log.info("Creating Pulsar tenant {}", pulsarTenant);
            admin.tenants().createTenant(pulsarTenant,
                    org.apache.pulsar.common.policies.data.TenantInfo.builder()
                            .allowedClusters(Set.copyOf(admin.clusters().getClusters()))
                            .build());
        }
    }

    private void ensureNamespaceExists(String namespace) throws PulsarAdminException {
        if (!admin.namespaces().getNamespaces(pulsarTenant).contains(namespace)) {
            log.info("Creating Pulsar namespace {}", namespace);
            admin.namespaces().createNamespace(namespace);
        }
    }

    private void grantNamespacePermission(String namespace, String role)
            throws PulsarAdminException {
        admin.namespaces().grantPermissionOnNamespace(namespace, role,
                EnumSet.of(AuthAction.produce, AuthAction.consume));
    }

    private String mintToken(String subject) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + tokenTtlDays * 24L * 3600L * 1000L);
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(signingKey, SignatureAlgorithm.RS256)
                .compact();
    }

    private static String roleFor(Tenant tenant) {
        return "tenant-" + tenant.getTenantId();
    }

    /**
     * Loads a PEM-encoded PKCS#8 RSA private key. Pulsar's
     * {@code pulsar tokens create-key-pair} produces this format by default.
     */
    private static PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = Files.readString(Path.of(path))
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    public record StreamingCredentials(String namespace, String role, String token) {}
}
