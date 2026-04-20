# ADR 0002 — Conserver ClickHouse (métriques) + Loki (logs)

**Status**: Accepted (2026-04-20)
**Scope**: Stockage des séries temporelles et logs ingérés par le path
streaming (ADR 0001) et le path bundle.

---

## Contexte

L'architecture actuelle (ADR 0000, ADR 0001) repose sur :
- **ClickHouse** pour les métriques host + JMX + benchmarks
  (`infra/clickhouse/init.sql`), avec TTL 90j et vues matérialisées
  hourly/daily consommées par le moteur IA.
- **Loki** pour les logs agrégés par `cluster_id`, `service`, `level`
  (`infra/loki/config.yml`), TTL 30j, intégration native Grafana.

Une question récurrente : ces choix restent-ils pertinents face aux
alternatives Grafana Mimir, VictoriaMetrics et VictoriaLogs ? Ce besoin
est devenu pressant en 2026-Q2 alors que la plateforme s'industrialise
et qu'il faut figer la stack avant le déploiement multi-régions.

Référence d'échelle : ~7,5 Go/jour de télémétrie pour un cluster L2 de
50 nœuds. Volume cible plateforme à 12 mois : O(50) clusters tenants
≈ 350 Go/jour ingérés.

## Décision

**Conserver ClickHouse pour les métriques. Conserver Loki pour les logs.**

Pas de migration vers Mimir / VictoriaMetrics / VictoriaLogs à
l'horizon de cette ADR.

## Alternatives considérées

### Grafana Mimir (métriques)

Pros :
- Compatible Prometheus remote-write : tout l'écosystème PromQL +
  alertmanager + recording rules est immédiatement disponible.
- Multi-tenant nativement par header `X-Scope-OrgID`.
- Object storage (S3/GCS) avec rétention longue à coût bas.

Cons rédhibitoires :
- Le moteur IA (`ClickHouseClient.java:1-209`) interroge
  `metric_points`, `jmx_points`, `benchmark_points` en SQL pour
  produire `mean / stddev / p95 / sample count` et joindre avec des
  données bundle PostgreSQL. Tout PromQL-iser implique réécrire le
  moteur IA et perdre les jointures relationnelles.
- Les vues matérialisées `metric_hourly_mv` et `metric_daily_mv`
  (init.sql:46-79) servent les requêtes IA en quelques ms — pas
  d'équivalent direct côté Mimir (les recording rules approchent mais
  sont moins flexibles).
- Cardinalité JMX Hadoop : composants nombreux (NameNode, DataNode,
  JournalNode, ResourceManager, NodeManager, RegionServer, Master, ZK,
  Hive Metastore, Hive Server, HMaster, Spark History, Knox, Ranger…)
  multipliés par les nœuds + métriques internes — explosion de séries
  difficile à maîtriser en Prometheus-style. ClickHouse encaisse ça
  comme une simple colonne `LowCardinality(String)`.
- Coût migration : agents (réécriture du module Python en
  remote-write), moteur IA (toutes les requêtes), infra (chart Helm),
  docs. Sans gain fonctionnel.

### VictoriaMetrics (métriques)

Mêmes contraintes que Mimir côté protocole + moteur IA. Avantage de
VM : binaire unique, RAM faible, ingestion brute supérieure à
Prometheus. Mais reste un TSDB Prometheus-compatible : ne convient
pas au modèle wide-column de SupportPlane.

### VictoriaLogs (logs)

Pros :
- Plus efficace que Loki au TB/jour (compression, indexation par
  flux).
- LogsQL plus expressif.
- Faible empreinte mémoire.

Cons :
- Projet jeune (GA 2024) ; intégration Grafana encore en
  développement vs Loki natif.
- Écosystème plus restreint (alerting, exporters, communauté).
- Bénéfice significatif uniquement à grande échelle (TB/jour) ;
  prématuré à 7,5 Go/jour/cluster.

### ClickHouse + Loki (choisi)

Pros :
- ClickHouse colle au modèle dimensionnel des métriques SupportPlane.
- Vues matérialisées exploitées par le moteur IA déjà en production.
- TTL natif, partition mensuelle, pas de tuning à l'échelle visée.
- Loki couvre 100 % du besoin logs actuel et reste cohérent avec le
  Grafana déjà déployé (`docker-compose.infra.yml:32-40`).
- Coût migration zéro.

Cons (acceptés) :
- Loki en mode single-instance n'est pas HA. Acceptable pour la
  rétention 30j et le volume actuel.
- ClickHouse demande une administration plus pointue qu'un binaire
  Prometheus-style à très grande échelle.

## Conséquences

Positives :
- Stabilité de la stack ; les évolutions (sécurité Pulsar — voir
  ADR 0003, durcissement ClickHouse, scale Loki si besoin) se font
  sans réécrire le moteur IA ni les agents.

Négatives / risques acceptés :
- Si un futur use case nécessite Prometheus (alerting natif,
  intégration vendor type Datadog), il faudra ajouter un
  exporter/sidecar plutôt que migrer la base.
- ClickHouse n'a pas d'équivalent Loki natif pour les logs : on garde
  deux moteurs distincts. C'est un compromis volontaire.

## Triggers de réévaluation

Réouvrir cette décision si l'un des points suivants devient vrai :

1. L'ingestion dépasse **100 Go/jour par cluster** ou **2 To/jour
   plateforme** (au-delà, VictoriaMetrics/VictoriaLogs deviennent
   économiquement intéressants).
2. Le moteur IA bascule vers un modèle PromQL-natif (par ex. parce
   qu'on intègre des règles d'alerting Prometheus partagées avec un
   client).
3. Loki pose un problème de scalabilité (latence requête > 5 s ou
   coût stockage > 30 % du TCO observabilité).
4. Une intégration vendor (Grafana Cloud, Coralogix…) impose un
   protocole spécifique en remote-write.
