# ADR 0003 — Sécurisation de l'exposition Pulsar (TLS + JWT + ACLs)

**Status**: Accepted (2026-04-20)
**Scope**: Sécurité du transport de streaming entre les agents
SupportCollector (installés sur les clusters clients) et le broker
Pulsar de SupportPlane (ADR 0001).

---

## Contexte

ADR 0001 a retenu Apache Pulsar comme transport pour le streaming
métriques + logs. La configuration livrée est minimale (mode
`standalone` exposé sur les ports 6650 / 8082) et **n'a aucune
sécurité** :

- Pas de TLS — le trafic agent → broker passe en clair.
- Pas d'authentification — n'importe qui peut se connecter au
  broker s'il a un accès réseau.
- Pas d'autorisation — l'isolation tenant repose uniquement sur la
  convention de nommage `persistent://supportplane/{tenant}/{topic}`.
  Un client malveillant ou un agent compromis peut produire dans le
  namespace d'un autre tenant et lire ses topics.

Pour exposer Pulsar à des agents installés en dehors du périmètre
maîtrisé (clusters clients sur leur propre WAN), ces trois manques
sont bloquants.

## Décision

Sécurisation en trois couches complémentaires :

1. **Transport** : TLS 1.2+ terminé sur **Pulsar Proxy** (pas
   directement sur les brokers).
2. **Authentification** : **JWT RS256** émis par le backend
   SupportPlane, un token par tenant.
3. **Autorisation** : **ACLs Pulsar par namespace** (`grant-permission`
   sur `produce`, `consume`).

Un module backend (`TenantStreamingService`) provisionne le namespace
et émet/rotationne le token au moment où l'opérateur active le
streaming pour un tenant.

## Alternatives considérées

### Authentification : mTLS par client

Pros : standard, robuste, pas de partage de secret hors PKI.
Cons :
- Gestion du cycle de vie des certificats clients (émission,
  renouvellement, révocation, CRL) à grande échelle = coût
  opérationnel élevé.
- Chaque agent doit gérer un keystore — friction d'installation côté
  client.

### Authentification : OAuth2 (client credentials vers IdP externe)

Pros : intégration naturelle avec Keycloak déjà déployé.
Cons :
- Aller-retour IdP au démarrage de chaque agent + refresh token =
  dépendance forte du streaming à la disponibilité de Keycloak.
- L'agent vit sur un réseau client (parfois sans accès direct
  Internet) — Keycloak doit alors être exposé séparément.

### Authentification : JWT RS256 (choisi)

Pros :
- **Asymétrique** : la clé privée n'existe que dans le backend
  SupportPlane (Secret K8s). Le broker et le proxy ne connaissent
  que la clé publique — compromission d'un broker = pas
  d'usurpation possible.
- Pas d'aller-retour à un IdP : l'agent transmet juste un token
  bearer dans la handshake Pulsar.
- Rotation supportée nativement par Pulsar (acceptation simultanée
  de deux clés publiques pendant la fenêtre de bascule).
- Token longue durée (1 an par défaut, rotatif) : pas de coût
  réseau récurrent.
- Support natif côté `pulsar-client` Java + Python.

Cons (acceptés) :
- Révocation = re-émission + suppression du grant. Pas de blacklist
  fine-grain.
- La clé privée du backend devient un secret critique : doit vivre
  dans un Secret K8s, idéalement géré par un secret manager (Vault,
  AWS Secrets Manager) en production.

### Transport : TLS direct sur les brokers

Cons :
- Chaque broker pod nécessite un cert public + un LoadBalancer
  individuel (ou un type LB head-of-line + DNS-as-a-service).
- Les brokers se retrouvent en bordure réseau, exposant leur
  surface d'attaque.

### Transport : Pulsar Proxy (choisi)

Pros :
- Un seul cert serveur (`pulsar-proxy.supportplane.io`), géré par
  cert-manager + Let's Encrypt.
- Brokers / bookies / ZK restent dans le réseau privé.
- Le proxy gère la découverte de partitions / lookup, donc les
  agents n'ont pas besoin d'adresses IP individuelles des brokers.
- Scale horizontal du proxy indépendant du broker.

Cons :
- Hop supplémentaire (proxy → broker), mais latence négligeable
  (intra-cluster K8s).

## Architecture retenue

### Topologie réseau

```
[ Agent SupportCollector (cluster client) ]
                │
                │  pulsar+ssl://pulsar-proxy.supportplane.io:6651
                │  (TLS one-way + JWT bearer)
                ▼
    [ Service K8s LoadBalancer (NLB) ]
                │
                ▼
       [ Pulsar Proxy (replicas: 2+) ]
                │  TLS interne (CA cert-manager)
                ▼
       [ Brokers + Bookies + ZK ]
                │
                ▼
       [ ClickHouse / Loki / PostgreSQL (ClusterIP) ]
```

### Authentification — flow d'émission de token

Nouveau endpoint backend :

```
POST /api/tenants/{tenantId}/streaming/enable
  → 1. crée le namespace `supportplane/{tenantId}` (PulsarAdmin)
  → 2. signe un JWT { sub: "tenant-{tenantId}", iat, exp: +1y }
       avec la clé privée RS256 du backend
  → 3. grant-permission --role tenant-{tenantId}
       --actions produce,consume supportplane/{tenantId}
  → 4. retourne { serviceUrl, token, caCertPem } (one-shot,
       token téléchargeable une fois en UI)

POST /api/tenants/{tenantId}/streaming/rotate-token
  → re-signe un nouveau JWT (subject inchangé), retourne le nouveau
     token. L'ancien reste valide jusqu'à expiration ou révocation
     manuelle (suppression du grant + ajout d'un nouveau grant pour
     un nouveau subject si besoin).
```

Tokens superuser pour le backend SupportPlane lui-même : subject
`supportplane-backend`, listé dans `superUserRoles` côté broker.

### Autorisation

Matrice de permissions appliquée par `pulsar-admin namespaces
grant-permission` :

| Sujet JWT (`sub`) | Namespace | Actions |
|---|---|---|
| `tenant-acme` | `supportplane/acme` | produce, consume |
| `tenant-bigco` | `supportplane/bigco` | produce, consume |
| `supportplane-backend` | `*` (superuser) | toutes |

Le broker rejette toute opération hors permissions accordées : un
agent compromis du tenant `acme` ne peut **pas** publier dans
`supportplane/bigco`.

### Rotation des clés

- Pulsar accepte plusieurs clés publiques dans `tokenPublicKey`
  (séparées par virgule ou via fichier multi-clés).
- Procédure de rotation :
  1. Générer une nouvelle paire `RS256_v2`.
  2. Ajouter `RS256_v2.public` à la config broker/proxy → restart
     rolling.
  3. Switcher le backend sur `RS256_v2.private` pour signer les
     nouveaux tokens.
  4. Attendre la fenêtre de transition (ex. 30 jours, le temps que
     les agents rafraîchissent leur token).
  5. Retirer `RS256_v1.public` → rolling restart broker/proxy.

## Conséquences

Positives :
- Pulsar peut être exposé sur Internet (ou tout réseau client)
  sans risque d'usurpation cross-tenant.
- L'isolation tenant devient garantie par le broker, pas par la
  convention de nommage.
- Provisioning self-service via UI SupportPlane (l'opérateur clique
  "activer streaming" et obtient un fichier `pulsar-token` à
  installer côté client).

Négatives / risques acceptés :
- Une clé privée RS256 par environnement à gérer (Secret K8s).
  Compromise = revocation de tous les tokens de l'env.
- Dépendance opérationnelle au Pulsar Proxy (mais : 2 replicas
  minimum, pas de stateful state).
- Les ports 6650 / 8082 doivent être fermés en production (via
  NetworkPolicy K8s ou règles firewall LB).
- En dev (docker-compose), l'auth est activée avec un token unique
  partagé pour éviter la divergence dev/prod, mais ça reste un
  secret dev.

## Implémentation

### Phasage

| Phase | Cible | Contenu |
|---|---|---|
| 1 | Dev (docker-compose) | Cert auto-signé + clé RS256 dev générés au premier `up` par sidecar `pulsar-init`. Token unique partagé via `.env`. |
| 2 | Staging | cert-manager + Let's Encrypt staging. Paire RS256 par env, tokens test. Validation rotation. |
| 3 | Production | cert-manager + Let's Encrypt prod. Émission tokens par tenant via UI. Audit log + endpoint rotation. Fermeture 6650 / 8082. Migration éventuelle de `pulsar standalone` vers `apache/pulsar-helm-chart` (broker + proxy + bookie + ZK séparés) — la config auth/TLS reste identique. |

### Fichiers touchés

| Fichier | Modification |
|---|---|
| `infra/pulsar/standalone.conf` | **Nouveau** — `authenticationEnabled=true`, `authorizationEnabled=true`, providers Token, `tokenPublicKey=file:///pulsar/keys/public.key`, `superUserRoles=supportplane-backend`, `tlsEnabled=true`. |
| `infra/pulsar/init/init.sh` | **Nouveau** — sidecar dev : génère cert auto-signé + paire RS256 + token superuser à la première exécution. |
| `docker-compose.infra.yml` | Service `pulsar-init` ajouté ; service `pulsar` reconfiguré pour charger les overrides. ClickHouse `CLICKHOUSE_PASSWORD` via `.env`. |
| `backend/pom.xml` | Ajout `io.jsonwebtoken:jjwt-*` (signature JWT) et `org.apache.pulsar:pulsar-client-admin` (provisioning namespace). |
| `backend/src/main/java/varga/supportplane/infra/PulsarClient.java` | Builder enrichi : `tlsTrustCertsFilePath` + `authentication(AuthenticationFactory.token(...))`. |
| `backend/src/main/resources/application.yml` | Nouveaux blocs `pulsar.auth.token`, `pulsar.tls.trust-certs-file-path`, `pulsar.admin.url`, `pulsar.jwt.private-key-path`, `pulsar.jwt.token-ttl-days`. |
| `backend/src/main/java/varga/supportplane/service/TenantStreamingService.java` | **Nouveau** — émission JWT, provisioning namespace + grants via `PulsarAdmin`. |
| `backend/src/main/java/varga/supportplane/controller/TenantStreamingController.java` | **Nouveau** — endpoints enable / rotate-token. |
| `helm/supportplane/values.yaml` | Blocs `pulsar.proxy`, `pulsar.auth`, `backend.pulsar`. |
| `helm/supportplane/templates/pulsar-proxy-deploy.yaml` | **Nouveau**. |
| `helm/supportplane/templates/pulsar-jwt-secret.yaml` | **Nouveau**. |
| `../supportcollector/odpsc-mpack/services/ODPSC/2.0/package/files/pulsar_streamer.py` | Lecture token + chemin CA depuis config Ambari ; passage à `pulsar.AuthenticationToken` + `tls_trust_certs_file_path`. |

## Triggers de réévaluation

Réouvrir cette décision si :
1. La gestion de la PKI client devient acceptable pour l'équipe
   (mTLS prend alors le relais comme standard).
2. Keycloak est exposé publiquement et les agents y ont accès — un
   flow OAuth2 device code devient envisageable.
3. Le nombre de tenants dépasse O(1000) — il faudra alors
   automatiser intégralement la rotation et envisager un secret
   manager dédié à la place du Secret K8s simple.
