# Kubernetes deployment guide

This guide walks through deploying the full SupportPlane stack on a Kubernetes
cluster, including the upstream dependencies (ingress controller, cert-manager,
optional observability stack).

## Architecture overview

```
                    ┌────────────────────┐
                    │  ingress-nginx     │  (cluster-wide)
                    └─────────┬──────────┘
                              │  TLS (cert-manager)
           ┌──────────────────┼──────────────────┐
           │                  │                  │
     host: supportplane.…     │        host: keycloak.…
     paths: / , /api          │        path : /
           │                  │                  │
 ┌─────────▼────────┐  ┌──────▼──────┐   ┌───────▼────────┐
 │  frontend (SPA)  │  │   backend   │   │    Keycloak    │
 │  React + nginx   │  │  Spring     │   │    24.0        │
 └──────────────────┘  │  Boot 3.2   │   └────────┬───────┘
                       │  port 8081  │            │
                       └──┬───┬───┬──┘            │
                          │   │   │               │
             ┌────────────┘   │   └────────────┐  │
             │                │                │  │
     ┌───────▼──────┐  ┌──────▼─────┐  ┌───────▼──▼───┐
     │  PostgreSQL  │  │  ClickHouse│  │  Loki + Pulsar│
     │  15 (STS)    │  │  (opt.)    │  │  (opt.)       │
     └──────────────┘  └────────────┘  └───────────────┘
                                    ▲
                         ┌──────────┴──────┐
                         │     Grafana     │  (opt., dashboards)
                         └─────────────────┘
```

## Prerequisites

| Tool | Minimum version |
| ---- | --------------- |
| Kubernetes | 1.27 |
| `kubectl` | matching cluster |
| `helm` | 3.14 |
| Storage class | one RWO class (postgres, bundles). RWX recommended for multi-replica backend. |
| Ingress controller | installed separately — examples use `ingress-nginx` |
| cert-manager | optional, for automatic Let's Encrypt certs |

## 1. Install upstream cluster add-ons

These are separate releases from SupportPlane. Install them **once per cluster**.

### 1.1 ingress-nginx

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.service.type=LoadBalancer \
  --set controller.config.proxy-body-size="500m" \
  --set controller.config.proxy-read-timeout="300" \
  --set controller.config.proxy-send-timeout="300"
```

> The `proxy-body-size` bump is required because SupportPlane accepts support
> bundles up to 500 MB.

### 1.2 cert-manager (optional — only if you want automated TLS)

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update

helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --set crds.enabled=true
```

Create a `ClusterIssuer` for Let's Encrypt:

```yaml
# cluster-issuer.yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: ops@example.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
      - http01:
          ingress:
            class: nginx
```

```bash
kubectl apply -f cluster-issuer.yaml
```

### 1.3 (Optional) metrics-server

Required for the HPA in `values-prod.yaml`. Most managed clusters (GKE, EKS,
AKS) already ship it.

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

### 1.4 (Optional) External-Secrets / Sealed-Secrets

If you prefer not to pass passwords through `--set`, install one of these and
create a Secret named `supportplane-secrets` with keys:
`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN`,
`KEYCLOAK_ADMIN_PASSWORD`, `APP_ENCRYPTION_KEY`
(plus `CLICKHOUSE_USER`, `CLICKHOUSE_PASSWORD` if ClickHouse is enabled), then
pass `--set secrets.existingSecret=supportplane-secrets`.

## 2. Build and push the application images

The chart does not pull from a public registry — you must build and push the
two application images.

```bash
# From the repo root
docker build -t <registry>/supportplane-backend:<tag>  backend/
docker build -t <registry>/supportplane-frontend:<tag> frontend/
docker push  <registry>/supportplane-backend:<tag>
docker push  <registry>/supportplane-frontend:<tag>
```

Private registry? Create a pull secret and pass it to the chart:

```bash
kubectl -n supportplane create secret docker-registry regcred \
  --docker-server=<registry> --docker-username=<u> --docker-password=<p>

# Then at install time:
--set imagePullSecrets[0].name=regcred
```

## 3. Install the SupportPlane umbrella chart

Pull the subchart dependencies (one-off, required only if you enable any of
the observability subcharts):

```bash
helm dependency update ./helm/supportplane
```

### 3.1 Dev profile (single-node k8s, everything in-cluster)

```bash
helm upgrade --install supportplane ./helm/supportplane \
  -f ./helm/supportplane/profiles/values-dev.yaml \
  --set backend.image.repository=<registry>/supportplane-backend \
  --set backend.image.tag=<tag> \
  --set frontend.image.repository=<registry>/supportplane-frontend \
  --set frontend.image.tag=<tag> \
  -n supportplane --create-namespace
```

Then port-forward to reach the UI:

```bash
kubectl -n supportplane port-forward svc/supportplane-frontend 3000:80
```

### 3.2 Production profile (HA + ingress + TLS)

1. Edit `profiles/values-prod.yaml` — set hostnames, storage classes, and CORS origin.
2. Create the Secret with real passwords (or let the chart generate from values — **not recommended for prod**):

```bash
kubectl -n supportplane create namespace supportplane
kubectl -n supportplane create secret generic supportplane-secrets \
  --from-literal=POSTGRES_DB=supportplane \
  --from-literal=POSTGRES_USER=supportplane \
  --from-literal=POSTGRES_PASSWORD=$(openssl rand -hex 24) \
  --from-literal=KEYCLOAK_ADMIN=admin \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -hex 24) \
  --from-literal=APP_ENCRYPTION_KEY=$(openssl rand -base64 32)
```

3. Install:

```bash
helm upgrade --install supportplane ./helm/supportplane \
  -f ./helm/supportplane/profiles/values-prod.yaml \
  --set secrets.existingSecret=supportplane-secrets \
  --set backend.image.repository=<registry>/supportplane-backend \
  --set backend.image.tag=<tag> \
  --set frontend.image.repository=<registry>/supportplane-frontend \
  --set frontend.image.tag=<tag> \
  -n supportplane --create-namespace
```

### 3.3 With full observability (ClickHouse + Loki + Grafana + Pulsar)

Stack the observability overlay on top of dev or prod:

```bash
helm dependency update ./helm/supportplane

helm upgrade --install supportplane ./helm/supportplane \
  -f ./helm/supportplane/profiles/values-prod.yaml \
  -f ./helm/supportplane/profiles/values-observability.yaml \
  --set clickhouse.auth.password=$(openssl rand -hex 24) \
  --set grafana.adminPassword=$(openssl rand -hex 24) \
  -n supportplane --create-namespace
```

The backend is automatically wired to the in-cluster service names. You can
verify:

```bash
kubectl -n supportplane exec deploy/supportplane-backend -- env | grep -E 'CLICKHOUSE|LOKI|PULSAR'
```

### 3.4 External PostgreSQL

Point the chart at a managed DB (RDS, Cloud SQL, …):

```bash
helm upgrade --install supportplane ./helm/supportplane \
  --set postgres.enabled=false \
  --set postgres.external.host=db.internal \
  --set postgres.external.port=5432 \
  --set secrets.postgres.database=supportplane \
  --set secrets.postgres.username=supportplane \
  --set secrets.postgres.password=<secret> \
  -n supportplane --create-namespace
```

The chart will drop the in-cluster StatefulSet and use the external host for
both the backend and Keycloak.

## 4. Post-install checks

```bash
# All pods ready
kubectl -n supportplane get pods

# Keycloak realms imported
kubectl -n supportplane logs deploy/supportplane-keycloak | grep "Imported realm"

# Backend reachable
kubectl -n supportplane port-forward svc/supportplane-backend 8081:8081 &
curl http://localhost:8081/actuator/health

# DB migrations applied
kubectl -n supportplane logs deploy/supportplane-backend | grep "Flyway"
```

## 5. Upgrades

```bash
helm upgrade supportplane ./helm/supportplane \
  -f <same values file(s) used at install>
```

The backend Deployment annotates its pod template with a checksum of the
backend ConfigMap, so changes to `backend-config` trigger a rolling restart
automatically.

## 6. Uninstall

```bash
helm uninstall supportplane -n supportplane
kubectl delete namespace supportplane
```

PVCs are **not** deleted automatically (postgres data, bundle uploads,
ClickHouse data, Loki chunks). Delete them explicitly if that's what you want:

```bash
kubectl -n supportplane delete pvc -l app.kubernetes.io/instance=supportplane
```

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
| ------- | ------------ | --- |
| Backend pod in `CrashLoopBackOff` with `FATAL: database "supportplane" does not exist` | Postgres hasn't finished initializing | wait, or pre-create the database on your external DB |
| Keycloak stuck on `Waiting for postgres` | DB connection wrong | check `KC_DB_URL`, Secret values |
| `401 Unauthorized` from frontend | Keycloak realm not imported, or wrong `KEYCLOAK_AUTH_SERVER_URL` | inspect `supportplane-keycloak` logs for "Imported realm" |
| Backend 413 on bundle upload | ingress body size too small | set `nginx.ingress.kubernetes.io/proxy-body-size: "500m"` on the ingress (already in `values-prod.yaml`) |
| Bundle files disappear after pod restart | PVC is `ReadWriteOnce` and pods land on different nodes | switch to `ReadWriteMany` or pin `backend.replicas=1` |
| ClickHouse tables missing | init ConfigMap name mismatch with `clickhouse.initdbScriptsConfigMap` | make sure the value matches `<release>-supportplane-clickhouse-init` |

## 8. Chart layout reference

```
helm/supportplane/
├── Chart.yaml                   umbrella metadata + subchart deps
├── values.yaml                  default values (documented)
├── files/
│   └── clickhouse-init.sql      schema consumed by the ClickHouse ConfigMap
├── realms/
│   ├── realm-clients.json       Keycloak realm (tenants)
│   └── realm-support.json       Keycloak realm (support staff)
├── profiles/
│   ├── values-dev.yaml          dev overlay
│   ├── values-prod.yaml         prod overlay
│   └── values-observability.yaml analytics/streaming overlay
└── templates/
    ├── _helpers.tpl
    ├── serviceaccount.yaml
    ├── secrets.yaml
    ├── backend-{deployment,service,configmap}.yaml
    ├── frontend-{deployment,service,configmap}.yaml
    ├── postgres-{statefulset,service}.yaml
    ├── keycloak-{deployment,service,ingress,realms-configmap}.yaml
    ├── bundle-pvc.yaml
    ├── ingress.yaml
    ├── pdb.yaml
    ├── hpa.yaml
    ├── networkpolicy.yaml
    ├── clickhouse-init-configmap.yaml
    └── NOTES.txt
```

When you edit a Keycloak realm in `keycloak/realm-*.json`, also copy the file
into `helm/supportplane/realms/` so the ConfigMap picks up the change. The
chart keeps physical copies because `helm package` does not follow symlinks
reliably.
