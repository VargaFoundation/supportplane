# ADR 0001 — Apache Pulsar for continuous metric & log streaming

**Status**: Accepted (2026-04-20)
**Scope**: SupportPlane ingest path — real-time alternative to ZIP bundle uploads

---

## Context

The original ingest pipeline is pull-based: agents compress metrics + log
tails + configs into ZIP bundles (L1 hourly, L2 daily, L3 on demand) and
POST them to the SupportPlane master. Bundles are deserialised into
PostgreSQL JSONB and the AI engine runs on whatever snapshot is most recent.

This design has three limits that matter for a 50-node L2 cluster
generating ~7.5 GB/day of telemetry:

1. **Latency floor** — sub-minute anomalies (GC storm, NameNode RPC backlog,
   leader election flap) cannot be caught before the next bundle. At L1 that
   means a worst-case 1h detection delay.
2. **Coarse granularity** — metrics are collapsed into a monolithic JSONB.
   Per-node series are reconstructed in-memory by the AI engine from 200+
   blobs on every analysis pass (~100 MB of JSON parsing).
3. **Bursty load** — a large cluster uploads a single 150 MB ZIP at the top
   of every hour. The ingest path is idle 99% of the time and saturated 1%.

The end-to-end architecture (ADR 0000) already adds ClickHouse (metrics)
and Loki (logs) as structured stores. Those stores need a low-latency feed
that is decoupled from the bundle lifecycle. This ADR picks the transport.

## Decision

Use **Apache Pulsar** as the streaming transport from agents to the
SupportPlane ingest consumer.

- Topic layout: `persistent://supportplane/{tenant-namespace}/{metrics|logs}`
- Tenant isolation: one Pulsar *namespace* per SupportPlane tenant.
  Quotas, retention, and geo-replication are applied at the namespace
  level by the broker — the application does not have to reimplement them.
- Producers (agents): `pulsar-client` Python library; batched, LZ4
  compressed, non-blocking async send. Hard-fails fast if the broker is
  down — the existing bundle upload path is the fallback.
- Consumer (SupportPlane): `StreamingConsumer` Spring component using
  `Shared` subscriptions so multiple SupportPlane replicas load-balance.
- The legacy bundle upload path **remains the source of truth** for configs,
  thread dumps, and heavy diagnostic artifacts. Streaming complements it,
  it does not replace it.

## Options considered

### Apache Kafka

Pros: Mature, excellent ecosystem, rock-solid for partitioned pub/sub,
lower operational overhead than Pulsar in the single-namespace case.

Cons:
 - No native multi-tenancy. Isolation has to be layered on top with
   topic naming conventions, ACLs, and per-client quotas — doable but
   every new tenant is a config change.
 - Tiered storage (KIP-405 / Confluent Tiered Storage) is either in a
   managed product or requires tuning the LocalTieredLogSegment feature,
   which is still maturing in the Apache distro as of 2026.
 - Geo-replication is MirrorMaker 2 — a separate deployment concern.

### Apache Pulsar (chosen)

Pros:
 - Native tenants + namespaces. A SupportPlane "tenant" maps 1:1 to a
   Pulsar namespace — quotas, retention, auth, and replication are
   per-namespace by construction. This is the load-bearing reason.
 - Tiered storage (BookKeeper → S3/GCS/filesystem) is a first-class
   broker feature; cold data is offloaded transparently with no change
   to consumers.
 - Geo-replication is built into the broker, driven by namespace policy.
 - Schema registry is built-in, not an external service.
 - Shared / Key_Shared / Failover subscriptions cover both broadcast and
   competing-consumer cases without a rewrite.

Cons:
 - Operational complexity: the broker depends on both ZooKeeper (metadata)
   and BookKeeper (log storage). That is three processes per control
   plane vs Kafka's one (or zero with KRaft). We mitigate this with the
   `apachepulsar/pulsar standalone` image for dev and by recommending a
   managed Pulsar (StreamNative / DataStax Astra Streaming) for production
   deployments that don't want to run the stack themselves.
 - Smaller operator community than Kafka — fewer Stack Overflow answers,
   fewer Helm charts, fewer pre-built exporters.
 - Harder to reason about resource usage (broker + bookie + ZK) when
   sizing a cluster.

### NATS JetStream

Pros: Dramatically simpler (one binary, no ZK, no BookKeeper), very low
latency, native multi-account isolation.

Cons:
 - Retention is measured in hours/days by default — not well-suited to the
   90-day metric horizon we want without tiered storage.
 - No built-in tiered-storage story; replaying old data means keeping it
   all in JetStream.
 - Weaker at very large fan-in (thousands of producers) than Kafka or
   Pulsar in public benchmarks.

For SupportPlane deployments where streaming is a light complement and
the customer does *not* need long streaming retention, NATS would be a
valid alternative. We keep the door open by making the streaming path
opt-in and narrow (two topics, JSON envelopes) — swapping transports later
would touch `PulsarClient.java`, `StreamingConsumer.java`, and
`pulsar_streamer.py`, nothing else.

### Direct HTTP push to SupportPlane + in-process buffering

Pros: Zero new infrastructure.

Cons: We were already doing a simpler version of this for bundles and hit
the limits described above — there is no backpressure, no replay, no
multi-consumer fan-out, and the ingest service becomes a single point of
failure for every agent in the fleet.

## Consequences

Positive:
 - Sub-minute metric visibility once an agent is streaming.
 - Per-tenant isolation and quotas come "for free" at the namespace level.
 - The AI engine can be re-run against historical windows by replaying
   from Pulsar's offloaded tier without touching PostgreSQL.
 - Future capabilities (Pulsar Functions for in-broker stream processing,
   Pulsar IO connectors to sink directly into ClickHouse) become available
   without changing the agent-side contract.

Negative / accepted risks:
 - Every production deployment that turns streaming on now has to operate
   (or pay for) a Pulsar cluster. Streaming is therefore **opt-in**
   (`pulsar.enabled=false` by default) and the feature is marketed as a
   premium add-on.
 - The streaming path and the bundle path can disagree in edge cases
   (e.g., an agent publishes a metric then the bundle arrives with a
   slightly different value). The AI engine consumes both but labels the
   source (`source=stream` vs `source=bundle`) so disagreements are
   auditable.
 - We own two Java classes (`PulsarClient`, `StreamingConsumer`) and one
   Python module (`pulsar_streamer.py`) that are dead weight for tenants
   who never turn streaming on. Kept small on purpose for this reason.

## Implementation notes

- Default config ships with `pulsar.enabled=false`. Turning it on requires
  configuring `pulsar.service-url` plus creating the tenant/namespace via
  `pulsar-admin tenants create supportplane` and
  `pulsar-admin namespaces create supportplane/<tenant>`.
- Agent-side, the streamer uses `send_async` with a non-blocking send
  queue; if the broker is unreachable the local queue drops messages and
  the agent keeps running. Durability remains with bundles.
- The consumer side uses `Shared` subscriptions so a SupportPlane HA pair
  can run two replicas without message duplication.
- For dev, `docker-compose -f docker-compose.infra.yml up pulsar` starts
  Pulsar standalone alongside ClickHouse and Loki.

## Revisit triggers

Re-open this decision if any of the following become true:
 1. We decide to offer streaming as the default and `pulsar.enabled=true`
    in the shipped config — then the operational cost applies to every
    customer and NATS JetStream might be a better fit.
 2. We add a second streaming use case (e.g., audit event bus, command &
    control for agents) that needs broadcast or request/reply semantics
    not served well by Pulsar subscriptions.
 3. A managed Kafka offering adds native multi-tenancy (tenant-scoped
    topics with per-tenant quotas) at a lower TCO than managed Pulsar.
