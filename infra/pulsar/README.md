# Pulsar dev configuration

This directory holds the configuration overrides + bootstrap scripts that
turn the `apachepulsar/pulsar:3.2.2` standalone image into an
**authenticated, TLS-secured** broker for development and CI.

See `docs/adr/0003-pulsar-security.md` for the design rationale.

## Layout

| Path | Purpose |
|---|---|
| `conf/standalone.conf` | Override mounted on top of the image's default standalone.conf — enables JWT auth, namespace ACLs, TLS on 6651/8443. |
| `init/init.sh` | One-shot bootstrap (run by the `pulsar-init` sidecar in `docker-compose.infra.yml`). Generates a self-signed CA + broker cert, an RS256 keypair, and a superuser JWT for the SupportPlane backend. Idempotent — re-running it is a no-op if the material already exists. |

## Volumes shared with the backend

The `pulsar-keys` volume is mounted in two places:
- Read-write into the `pulsar` container so the broker reads
  `jwt-public.pem` (`tokenPublicKey=file:///pulsar/keys/jwt-public.pem`).
- Read-only into the `supportplane-backend` service so it can read
  `superuser.token` (consumed as `PULSAR_AUTH_TOKEN`) and `jwt-private.pem`
  (used by `TenantStreamingService` to mint per-tenant tokens).

The `pulsar-certs` volume is mounted read-only into the backend so it can
verify the broker's TLS cert (`PULSAR_TLS_TRUST_CERTS=/pulsar/certs/ca.crt`).

## Production

In production the `init.sh` bootstrap is **not used**. The keys are
provisioned via cert-manager + a dedicated K8s `Secret` (see
`helm/supportplane/templates/pulsar-jwt-secret.yaml`) and the backend
mounts those Secrets directly. The on-disk paths inside the container
remain the same so the application code is identical.
