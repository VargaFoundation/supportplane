package varga.supportplane.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Apache Pulsar client facade for continuous streaming of metrics and logs.
 *
 * Topic convention: persistent://supportplane/{tenantId}/{topic}
 *   - metrics     : raw numeric data points from agents
 *   - logs        : structured log records
 *   - bundles     : bundle-manifest events (optional audit trail)
 *
 * Multi-tenant isolation is enforced at the namespace level (tenantId),
 * letting us apply per-customer quotas, retention, and geo-replication.
 *
 * Disabled by default (pulsar.enabled=false) — the legacy bundle upload
 * path continues to work without Pulsar.
 */
@Component
@Slf4j
public class PulsarClient {

    @Value("${pulsar.enabled:false}")
    private boolean enabled;

    @Value("${pulsar.service-url:pulsar://localhost:6650}")
    private String serviceUrl;

    @Value("${pulsar.tenant:supportplane}")
    private String tenant;

    @Value("${pulsar.default-namespace:default}")
    private String defaultNamespace;

    @Value("${pulsar.producer.batching-max-messages:1000}")
    private int batchingMaxMessages;

    @Value("${pulsar.producer.batching-max-delay-ms:10}")
    private int batchingMaxDelayMs;

    /** Bearer JWT used to authenticate against the broker. Empty = no auth (legacy/dev). */
    @Value("${pulsar.auth.token:}")
    private String authToken;

    /** PEM file containing the CA cert used to verify the broker's TLS cert. Empty = no TLS. */
    @Value("${pulsar.tls.trust-certs-file-path:}")
    private String tlsTrustCertsFilePath;

    private org.apache.pulsar.client.api.PulsarClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Producer<byte[]>> producers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Pulsar streaming disabled (pulsar.enabled=false)");
            return;
        }
        try {
            ClientBuilder builder = org.apache.pulsar.client.api.PulsarClient.builder()
                    .serviceUrl(serviceUrl)
                    .ioThreads(2)
                    .listenerThreads(2)
                    .operationTimeout(10, TimeUnit.SECONDS);
            applySecurity(builder);
            client = builder.build();
            log.info("Pulsar client connected to {} (auth={}, tls={})",
                    serviceUrl, !authToken.isBlank(), !tlsTrustCertsFilePath.isBlank());
        } catch (PulsarClientException e) {
            log.warn("Pulsar not available at {}: {}. Streaming will be skipped.", serviceUrl, e.getMessage());
        }
    }

    private void applySecurity(ClientBuilder builder) throws PulsarClientException {
        if (!tlsTrustCertsFilePath.isBlank()) {
            builder.tlsTrustCertsFilePath(tlsTrustCertsFilePath)
                    .allowTlsInsecureConnection(false)
                    .enableTlsHostnameVerification(false);
        }
        if (!authToken.isBlank()) {
            builder.authentication(AuthenticationFactory.token(authToken));
        }
    }

    @PreDestroy
    public void shutdown() {
        producers.values().forEach(p -> {
            try { p.close(); } catch (Exception ignored) {}
        });
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    public boolean isEnabled() {
        return enabled && client != null;
    }

    public String topicFor(String tenantId, String topic) {
        String ns = tenantId == null || tenantId.isBlank() ? defaultNamespace : tenantId;
        return "persistent://" + tenant + "/" + ns + "/" + topic;
    }

    /**
     * Publish an arbitrary payload to a tenant-scoped topic.
     * Messages are keyed by clusterId+nodeId so they route to the same partition.
     */
    public void publish(String tenantId, String topic, String routingKey, Object payload) {
        if (!isEnabled()) return;
        String topicName = topicFor(tenantId, topic);
        try {
            Producer<byte[]> producer = producers.computeIfAbsent(topicName, this::createProducer);
            byte[] data = mapper.writeValueAsBytes(payload);
            producer.newMessage()
                    .key(routingKey)
                    .value(data)
                    .sendAsync()
                    .exceptionally(ex -> {
                        log.warn("Pulsar publish failed on {}: {}", topicName, ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.warn("Failed to publish to {}: {}", topicName, e.getMessage());
        }
    }

    private Producer<byte[]> createProducer(String topicName) {
        try {
            return client.newProducer(Schema.BYTES)
                    .topic(topicName)
                    .enableBatching(true)
                    .batchingMaxMessages(batchingMaxMessages)
                    .batchingMaxPublishDelay(batchingMaxDelayMs, TimeUnit.MILLISECONDS)
                    .blockIfQueueFull(false)
                    .sendTimeout(30, TimeUnit.SECONDS)
                    .compressionType(CompressionType.LZ4)
                    .create();
        } catch (PulsarClientException e) {
            throw new IllegalStateException("Cannot create Pulsar producer for " + topicName, e);
        }
    }

    /**
     * Subscribe to a topic with a message listener. Uses Shared subscription so
     * multiple consumer instances load-balance in a scale-out deployment.
     */
    public Consumer<byte[]> subscribe(String tenantId, String topic, String subscriptionName,
                                       MessageListener<byte[]> listener) throws PulsarClientException {
        if (!isEnabled()) {
            throw new IllegalStateException("Pulsar client not initialized");
        }
        String topicName = topicFor(tenantId, topic);
        return client.newConsumer(Schema.BYTES)
                .topic(topicName)
                .subscriptionName(subscriptionName)
                .subscriptionType(SubscriptionType.Shared)
                .ackTimeout(60, TimeUnit.SECONDS)
                .negativeAckRedeliveryDelay(5, TimeUnit.SECONDS)
                .receiverQueueSize(1000)
                .messageListener(listener)
                .subscribe();
    }
}
