-- V12: Final comprehensive rules — Tez, Sqoop, Kafka Connect, Flink, Knox deep,
-- HBase advanced, ZK advanced, OS hardening, network deep, hardware, DR/HA final,
-- Oozie deep, Solr deep, cloud patterns, operational maturity.

INSERT INTO recommendation_rules (code, title, description, category, subcategory, component, threat, vulnerability, asset, impact, default_likelihood, default_severity, recommendations_text, condition) VALUES

-- ============================================================
-- TEZ EXECUTION ENGINE
-- ============================================================

('TEZ-CONTAINER-REUSE', 'Tez container reuse',
 'Container reuse avoids the overhead of launching new YARN containers for each task, critical for short-running tasks.',
 'Performance', 'Tez', 'Hive',
 '["High task launch overhead"]', '["Container reuse disabled"]', '["Hive", "Yarn"]',
 'Without reuse, every Tez task pays 3-5s container launch overhead.',
 'MEDIUM', 'WARNING',
 '["Set tez.am.container.reuse.enabled=true (default)", "Set tez.am.container.reuse.rack-fallback.enabled=true", "Set tez.am.container.idle.release-timeout-min.millis=10000", "Set tez.am.container.idle.release-timeout-max.millis=20000"]',
 '{"type": "always"}'),

('TEZ-AM-MEMORY', 'Tez ApplicationMaster memory',
 'Tez AM coordinates all tasks in a DAG. Under-sized AM causes OOM for complex queries.',
 'Performance', 'Tez', 'Hive',
 '["Complex query failures"]', '["AM heap too small for large DAGs"]', '["Hive"]',
 'Queries with 1000+ tasks generate large AM state that exceeds default heap.',
 'MEDIUM', 'WARNING',
 '["Set tez.am.resource.memory.mb=4096 for complex workloads", "Set tez.am.java.opts=-Xmx3276m (80% of container)", "Increase for queries with many stages or large shuffles", "Monitor AM memory via Tez UI"]',
 '{"type": "always"}'),

('TEZ-SESSION-MODE', 'Tez session mode for interactive queries',
 'Session mode keeps a Tez AM alive between queries, eliminating startup overhead for interactive use.',
 'Performance', 'Tez', 'Hive',
 '["High per-query startup cost"]', '["Session mode disabled"]', '["Hive"]',
 'Each Hive query without session mode launches a new AM (5-15s overhead).',
 'LOW', 'INFO',
 '["Set hive.server2.tez.initialize.default.sessions=true", "Set hive.server2.tez.default.queues=interactive", "Set hive.server2.tez.sessions.per.default.queue=2", "Session mode is automatic when using LLAP"]',
 '{"type": "always"}'),

-- ============================================================
-- SQOOP DATA TRANSFER
-- ============================================================

('SQOOP-CONN-MANAGER', 'Sqoop connection manager configuration',
 'Sqoop should use the appropriate connection manager for the target RDBMS.',
 'Performance', 'Sqoop', 'Platform',
 '["Slow imports", "Missing features"]', '["Generic JDBC manager"]', '["Data"]',
 'Generic JDBC misses database-specific optimizations (Oracle Direct, MySQL native).',
 'LOW', 'WARNING',
 '["Use --direct flag for MySQL/PostgreSQL native fast-path", "For Oracle: use OraOop connector for 10x faster imports", "Set --fetch-size=10000 for large imports", "Configure --split-by on indexed column for parallel import"]',
 '{"type": "always"}'),

('SQOOP-INCREMENTAL', 'Sqoop incremental import strategy',
 'Full table imports are wasteful. Incremental imports transfer only new/changed rows.',
 'Performance', 'Sqoop', 'Platform',
 '["Wasted I/O and compute"]', '["Full import every run"]', '["Data", "HDFS"]',
 'Re-importing 100M rows daily when only 100K changed wastes hours of compute and network.',
 'MEDIUM', 'WARNING',
 '["Use --incremental append --check-column id --last-value N for append-only tables", "Use --incremental lastmodified --check-column modified_date for updatable tables", "Store last-value in Sqoop metastore: --meta-connect jdbc:...", "Validate row counts after each incremental import"]',
 '{"type": "always"}'),

('SQOOP-PARALLELISM', 'Sqoop import parallelism',
 'Sqoop import parallelism must be tuned to balance speed against source database load.',
 'Performance', 'Sqoop', 'Platform',
 '["Source DB overload or slow import"]', '["Default 4 mappers"]', '["Data"]',
 'Too many mappers overwhelm the source DB. Too few make imports take hours.',
 'LOW', 'WARNING',
 '["Default --num-mappers=4 is conservative for most databases", "Increase to 8-16 for large tables with good DB performance", "Use --split-by on a well-distributed numeric column", "Monitor source DB CPU during imports — back off if >70%"]',
 '{"type": "always"}'),

-- ============================================================
-- KAFKA CONNECT & STREAMING
-- ============================================================

('KCONNECT-MONITORING', 'Kafka Connect connector monitoring',
 'Connector status and task failures must be monitored for data pipeline health.',
 'Operations', 'Kafka', 'Kafka',
 '["Silent connector failure"]', '["No connector monitoring"]', '["Kafka"]',
 'A failed connector stops data flow without any downstream alert.',
 'HIGH', 'WARNING',
 '["Monitor connector status via REST API: GET /connectors/{name}/status", "Alert on status=FAILED or task status=FAILED", "Implement auto-restart for failed tasks: errors.tolerance=all with DLQ", "Set up health check: poll /connectors every 60s"]',
 '{"type": "always"}'),

('KCONNECT-OFFSET-MGMT', 'Kafka Connect offset management',
 'Connect offsets track progress. Loss means re-processing from start or data gaps.',
 'Operations', 'Kafka', 'Kafka',
 '["Data reprocessing or gaps"]', '["Offset storage not durable"]', '["Kafka", "Data"]',
 'Offset loss forces connector to restart from beginning or latest, causing duplicates or gaps.',
 'MEDIUM', 'WARNING',
 '["Use offset.storage.topic with replication-factor=3", "Set offset.flush.interval.ms=60000 (not too aggressive)", "For source connectors: implement exactly-once via transactional producers", "Back up offset topic periodically"]',
 '{"type": "always"}'),

('KCONNECT-TRANSFORMS', 'Kafka Connect SMT best practices',
 'Single Message Transforms must be lightweight. Heavy transforms should use Kafka Streams instead.',
 'Performance', 'Kafka', 'Kafka',
 '["Connector bottleneck"]', '["Heavy processing in SMTs"]', '["Kafka"]',
 'CPU-intensive SMTs reduce connector throughput and increase processing latency.',
 'LOW', 'INFO',
 '["Use SMTs for: field renaming, timestamp conversion, routing, masking", "Move to Kafka Streams for: complex joins, windowed aggregations, stateful transforms", "Avoid: external API calls, large lookups, regex on every record in SMTs", "Monitor SMT processing time per record"]',
 '{"type": "always"}'),

('KSTREAMS-STATE-STORE', 'Kafka Streams state store management',
 'Kafka Streams state stores can grow large. RocksDB configuration and changelog compaction are critical.',
 'Performance', 'Kafka', 'Kafka',
 '["State store disk exhaustion", "Slow restarts"]', '["Default RocksDB config"]', '["Kafka"]',
 'Default RocksDB settings cause high memory usage and long restart times for large state.',
 'MEDIUM', 'WARNING',
 '["Configure RocksDB block cache: rocksdb.config.setter class", "Set state.dir on fast local disk (SSD preferred)", "Enable changelog topic compaction: cleanup.policy=compact", "Monitor state store size and restore time"]',
 '{"type": "always"}'),

-- ============================================================
-- KNOX DEEP
-- ============================================================

('KNOX-SSO', 'Knox SSO configuration',
 'Knox provides single sign-on for Hadoop web UIs (Ambari, HDFS, YARN, Oozie, etc.).',
 'Security', 'Authentication', 'Knox',
 '["Multiple login prompts for users"]', '["SSO not configured"]', '["Knox"]',
 'Without SSO, users authenticate separately to each web UI, poor user experience and credential exposure.',
 'MEDIUM', 'WARNING',
 '["Configure KnoxSSO provider in topology", "Set up SAML or OAuth2 identity provider integration", "Configure SSO cookie domain and path", "Enable SSO for all protected web UIs"]',
 '{"type": "always"}'),

('KNOX-HA', 'Knox Gateway high availability',
 'Multiple Knox gateways behind a load balancer prevent single point of failure for API access.',
 'Architecture', 'High Availability', 'Knox',
 '["Gateway SPOF"]', '["Single Knox instance"]', '["Knox"]',
 'Knox failure blocks all API and web UI access for users outside the cluster.',
 'MEDIUM', 'WARNING',
 '["Deploy 2+ Knox instances on different hosts", "Use HAProxy or F5 load balancer in front", "Configure sticky sessions for web UIs", "Shared topology configuration via ZooKeeper or shared filesystem"]',
 '{"type": "always"}'),

-- ============================================================
-- HBASE DEEP
-- ============================================================

('HBASE-MOB', 'HBase MOB (Medium Object) storage',
 'Objects 100KB-10MB should use MOB storage to avoid excessive compaction overhead.',
 'Performance', 'HBase', 'HBase',
 '["Compaction storms from large values"]', '["Large values in regular store files"]', '["HBase"]',
 'Regular HBase stores compacting large values rewrite GB of data unnecessarily.',
 'LOW', 'INFO',
 '["Enable MOB for column families with values 100KB-10MB: IS_MOB=>true, MOB_THRESHOLD=>102400", "MOB files are stored separately from regular HFiles", "MOB compaction is optimized for large values", "Not suitable for values <100KB (overhead) or >10MB (use HDFS directly)"]',
 '{"type": "always"}'),

('HBASE-COPROCESSOR-RISK', 'HBase coprocessor risk assessment',
 'Custom coprocessors run inside RegionServer JVM. Bugs crash the entire RS.',
 'Architecture', 'HBase', 'HBase',
 '["RegionServer crash from coprocessor bug"]', '["Untested custom coprocessors"]', '["HBase"]',
 'A coprocessor OOM or infinite loop kills the hosting RegionServer and all its regions.',
 'HIGH', 'CRITICAL',
 '["Thoroughly test coprocessors in staging before production", "Implement timeout: hbase.coprocessor.abortonerror=true", "Monitor coprocessor execution time via JMX", "Prefer server-side filters over coprocessors when possible", "Implement circuit breaker in coprocessor code"]',
 '{"type": "always"}'),

('HBASE-HBCK', 'HBase consistency check (hbck)',
 'Regular hbck runs detect and fix inconsistencies between HBase Master, RegionServers, and HDFS.',
 'Operations', 'HBase', 'HBase',
 '["Silent data inconsistency"]', '["No consistency checks"]', '["HBase"]',
 'Region assignment inconsistencies cause reads to return old data or fail silently.',
 'MEDIUM', 'WARNING',
 '["Schedule weekly: hbase hbck (read-only check)", "Review output for: inconsistencies, orphan regions, overlap", "HBase 2.x: use HBCK2 (hbck is different in HBase 2)", "Fix inconsistencies immediately — they worsen over time"]',
 '{"type": "always"}'),

-- ============================================================
-- ZOOKEEPER DEEP
-- ============================================================

('ZK-OBSERVER', 'ZooKeeper observer mode for read scaling',
 'Observers participate in the ensemble for reads but not writes, scaling read-heavy workloads.',
 'Architecture', 'ZooKeeper', 'ZooKeeper',
 '["ZK read bottleneck"]', '["All nodes are voting members"]', '["ZooKeeper"]',
 'Adding voting members beyond 5 increases write latency. Observers scale reads without this cost.',
 'LOW', 'INFO',
 '["Add observers for read-heavy clusters: peerType=observer in zoo.cfg", "Observers do not participate in elections (no write overhead)", "Use for: cross-datacenter ZK, read-heavy metadata lookups", "Minimum 3 voting members + N observers"]',
 '{"type": "always"}'),

('ZK-CONNECTION-TIMEOUT', 'ZooKeeper client connection timeout',
 'Client connection timeout affects how quickly services detect ZK unavailability.',
 'Performance', 'ZooKeeper', 'ZooKeeper',
 '["Slow failure detection or false timeouts"]', '["Default timeout not tuned"]', '["ZooKeeper"]',
 'Too short: network glitch causes session loss. Too long: real ZK failure goes undetected.',
 'LOW', 'WARNING',
 '["HBase: zookeeper.session.timeout=90000 (accommodate GC pauses)", "Kafka: zookeeper.connection.timeout.ms=18000", "Set based on: max_GC_pause + network_latency + safety_margin", "Monitor session timeout events via ZK mntr"]',
 '{"type": "always"}'),

-- ============================================================
-- OS HARDENING
-- ============================================================

('OS-CGROUPS-SERVICES', 'Cgroups for service resource isolation',
 'OS-level cgroups prevent one service (e.g., DataNode) from consuming all CPU or memory.',
 'Performance', 'OS', 'OS',
 '["One service starving others"]', '["No service-level cgroups"]', '["Servers"]',
 'A DataNode compaction storm can consume all CPU, making the colocated NodeManager unresponsive.',
 'MEDIUM', 'WARNING',
 '["Configure systemd resource limits per service: MemoryMax=, CPUQuota=", "Set CPUQuota=80% for DataNode to reserve CPU for other services", "Set MemoryMax for each service to prevent OOM of colocated services", "Use slice hierarchy: hadoop.slice for all Hadoop services"]',
 '{"type": "always"}'),

('OS-HUGEPAGES-JAVA', 'Huge pages for Java heap',
 'Huge pages reduce TLB misses for large Java heaps, improving throughput 2-5% for heap >8GB.',
 'Performance', 'OS', 'OS',
 '["TLB miss overhead"]', '["Standard 4KB pages for large heaps"]', '["Servers"]',
 'Large heaps with 4KB pages generate excessive TLB misses, adding CPU overhead.',
 'LOW', 'INFO',
 '["Allocate huge pages: sysctl vm.nr_hugepages=N (N = total_java_heap_MB / 2)", "Enable in JVM: -XX:+UseLargePages", "Note: THP (Transparent HugePages) should be disabled — use explicit huge pages", "Verify: grep HugePages /proc/meminfo"]',
 '{"type": "always"}'),

('OS-DISK-ALIGNMENT', 'Disk partition alignment',
 'Misaligned partitions cause double I/O for every write, halving disk performance.',
 'Performance', 'OS', 'OS',
 '["Halved disk throughput"]', '["Partition misalignment"]', '["Servers", "HDFS"]',
 'Legacy fdisk creates misaligned partitions. Modern gdisk/parted align correctly.',
 'LOW', 'INFO',
 '["Verify alignment: parted /dev/sdX align-check optimal 1", "New partitions: use parted with -a optimal flag", "Existing misaligned: backup data, repartition, restore", "Most modern OS installers align correctly — mainly a risk on old systems"]',
 '{"type": "always"}'),

('OS-SWAP-OFF', 'Swap disabled for Hadoop nodes',
 'Hadoop nodes should have swap disabled (or swappiness=1) to prevent any swapping.',
 'Performance', 'OS', 'OS',
 '["Severe latency from swapping"]', '["Swap partition active"]', '["Servers"]',
 'Any swapping causes 1000x latency increase, effectively freezing the service.',
 'HIGH', 'WARNING',
 '["Set vm.swappiness=1 (not 0 to avoid OOM kills)", "Consider swapoff -a for critical nodes (NameNode, HBase Master)", "If swap must exist: size it small (1GB max) as safety net", "Monitor swap usage: any non-zero swap_used is a problem"]',
 '{"type": "always"}'),

-- ============================================================
-- NETWORK DEEP
-- ============================================================

('NET-JUMBO-VALIDATE', 'Jumbo frame end-to-end validation',
 'Jumbo frames (MTU 9000) must be validated end-to-end: NIC, switch, and all intermediate hops.',
 'Performance', 'Network', 'Network',
 '["Packet fragmentation"]', '["Inconsistent MTU across path"]', '["Servers"]',
 'If one switch in the path has MTU 1500, jumbo frames are fragmented, adding overhead instead of reducing it.',
 'LOW', 'INFO',
 '["Test: ping -M do -s 8972 <target> (8972 = 9000 - 20 IP - 8 ICMP)", "Verify on all switches: show interface mtu", "Set consistently across ALL cluster NICs and ALL switches", "If any hop does not support jumbo, do not enable anywhere"]',
 '{"type": "always"}'),

('NET-DNS-REVERSE', 'DNS reverse lookup configuration',
 'Hadoop requires working reverse DNS (PTR records) for Kerberos and hostname verification.',
 'Architecture', 'Network', 'Network',
 '["Kerberos failures", "Connection refused"]', '["Missing reverse DNS"]', '["Servers", "Kerberos"]',
 'Kerberos requires forward and reverse DNS to match. Missing PTR records break authentication.',
 'HIGH', 'CRITICAL',
 '["Verify: nslookup <IP> returns correct FQDN for every node", "Configure PTR records in DNS for all cluster IPs", "Alternative: /etc/hosts with both FQDN and short name", "Test: hostname -f should return FQDN on every node"]',
 '{"type": "always"}'),

('NET-FIREWALL-PORTS', 'Hadoop service port firewall rules',
 'Firewall rules must allow all required Hadoop inter-service ports while blocking unnecessary access.',
 'Security', 'Network', 'Network',
 '["Service communication failures"]', '["Incorrect firewall rules"]', '["Servers"]',
 'Missing firewall rules break inter-service communication. Too permissive rules expose attack surface.',
 'MEDIUM', 'WARNING',
 '["Document all required ports per service (NN:8020/9870, DN:9866/9864, RM:8088, NM:8042, ZK:2181, HBase:16010/16030, etc.)", "Allow intra-cluster traffic on all Hadoop ports", "Block external access except gateway services (Knox:8443, HS2:10000)", "Test with: nmap from outside cluster to verify only expected ports open"]',
 '{"type": "always"}'),

-- ============================================================
-- HARDWARE DEEP
-- ============================================================

('INFRA-ECC-MEMORY', 'ECC memory for data integrity',
 'ECC (Error-Correcting Code) memory detects and corrects single-bit errors that cause silent data corruption.',
 'Architecture', 'Hardware', 'OS',
 '["Silent data corruption"]', '["Non-ECC memory"]', '["Servers", "Data"]',
 'Non-ECC memory has ~1 error per GB per year. For a 128GB server, thats 128 potential corruptions per year.',
 'MEDIUM', 'CRITICAL',
 '["Use ECC memory on all production cluster nodes", "Verify: dmidecode -t memory | grep Error", "Monitor: edac-util or mcelog for corrected errors", "Corrected errors are early warning — replace DIMM when count increases"]',
 '{"type": "always"}'),

('INFRA-NVME-ALIGNMENT', 'NVMe configuration for Hadoop',
 'NVMe drives require specific I/O scheduler and alignment settings for optimal performance.',
 'Performance', 'Hardware', 'OS',
 '["Suboptimal NVMe performance"]', '["Default HDD-oriented settings"]', '["Servers"]',
 'NVMe with CFQ scheduler and low queue depth operates at fraction of its capability.',
 'LOW', 'INFO',
 '["Set I/O scheduler to none/mq-deadline: echo none > /sys/block/nvmeXnY/queue/scheduler", "Set queue depth: echo 1024 > /sys/block/nvmeXnY/queue/nr_requests", "Disable write barriers for data disks: mount with nobarrier (ext4)", "Use fstrim for TRIM support on SSD/NVMe"]',
 '{"type": "always"}'),

('INFRA-RAID-REBUILD', 'RAID rebuild time estimation',
 'RAID rebuild time on large disks can take days, during which another failure causes data loss.',
 'Architecture', 'Hardware', 'OS',
 '["Data loss during rebuild"]', '["No rebuild time awareness"]', '["Servers"]',
 'A 12TB RAID5 rebuild takes 12-24 hours. A second disk failure during rebuild loses the array.',
 'MEDIUM', 'WARNING',
 '["Prefer JBOD with HDFS replication over RAID for DataNodes", "If RAID: use RAID10 (not RAID5/6) for faster rebuild", "Monitor RAID rebuild progress: cat /proc/mdstat or megacli/storcli", "HDFS already provides data redundancy — RAID adds unnecessary rebuild risk"]',
 '{"type": "always"}'),

('INFRA-PSU-REDUNDANCY', 'Power supply redundancy',
 'Dual power supplies with separate circuits prevent server outage from PSU failure.',
 'Architecture', 'Hardware', 'OS',
 '["Server outage from PSU failure"]', '["Single power supply"]', '["Servers"]',
 'Single PSU failure takes down the server. For NameNode/HMaster, this means cluster outage.',
 'MEDIUM', 'WARNING',
 '["Ensure dual PSU on critical nodes: NameNode, ResourceManager, HBase Master, ZooKeeper", "Connect PSUs to separate power circuits/UPS", "Monitor PSU health via IPMI/iLO", "DataNodes can use single PSU if HDFS replication provides redundancy"]',
 '{"type": "always"}'),

-- ============================================================
-- DR & HA FINAL
-- ============================================================

('DR-RUNBOOK-TESTED', 'DR runbook annually tested',
 'DR procedures must be tested end-to-end annually to verify they actually work.',
 'Architecture', 'Disaster Recovery', 'Platform',
 '["DR plan fails when needed"]', '["DR never tested"]', '["Data", "Servers"]',
 'A DR plan that has never been tested is a hope document, not a plan.',
 'HIGH', 'CRITICAL',
 '["Schedule annual DR drill: simulate primary site failure", "Execute full failover to DR site with production-representative data", "Measure: time to detect, time to failover, data loss (actual RPO)", "Update runbook based on drill findings — every drill reveals gaps"]',
 '{"type": "always"}'),

('HA-ZK-QUORUM-LOSS', 'ZooKeeper quorum loss recovery',
 'A documented procedure for recovering from ZK quorum loss prevents extended outages.',
 'Architecture', 'High Availability', 'ZooKeeper',
 '["Extended cluster-wide outage"]', '["No quorum recovery procedure"]', '["ZooKeeper", "HBase", "Kafka", "HDFS"]',
 'ZK quorum loss stops all dependent services: HDFS HA failover, HBase, Kafka — complete cluster down.',
 'HIGH', 'CRITICAL',
 '["Document quorum recovery steps: identify surviving nodes, verify data consistency", "If minority survives: start enough new ZK nodes to form new quorum", "If no survivors: restore from latest ZK snapshot", "Test recovery procedure in staging annually"]',
 '{"type": "always"}'),

('HA-NAMENODE-JOURNAL', 'NameNode JournalNode resilience',
 'JournalNodes store HDFS edit logs for HA. Their failure can block NameNode writes.',
 'Architecture', 'High Availability', 'HDFS',
 '["HDFS write failures"]', '["JournalNode resilience not tested"]', '["HDFS"]',
 'NameNode requires majority of JournalNodes for writes. If 2/3 fail, HDFS becomes read-only.',
 'HIGH', 'CRITICAL',
 '["Deploy 3 or 5 JournalNodes on separate hosts (never on DataNodes)", "Place on different racks for rack-level fault tolerance", "Monitor JournalNode health and sync status", "Test: stop one JournalNode and verify writes continue normally"]',
 '{"type": "always"}'),

-- ============================================================
-- OOZIE DEEP
-- ============================================================

('OOZIE-HA', 'Oozie server high availability',
 'Multiple Oozie servers provide failover for workflow orchestration.',
 'Architecture', 'High Availability', 'Oozie',
 '["Workflow scheduling failure"]', '["Single Oozie server"]', '["Oozie"]',
 'Single Oozie failure stops all scheduled workflows and coordinator jobs.',
 'MEDIUM', 'WARNING',
 '["Deploy 2+ Oozie servers with database-backed HA", "Use load balancer for client connections", "Configure oozie.services.ext for HA services", "Test failover: stop primary and verify jobs continue"]',
 '{"type": "always"}'),

('OOZIE-PURGE', 'Oozie completed job purge',
 'Completed workflow data accumulates in the Oozie database, degrading performance over time.',
 'Operations', 'Oozie', 'Oozie',
 '["Oozie DB performance degradation"]', '["No purge configured"]', '["Oozie"]',
 'Millions of completed workflow records slow Oozie queries and UI rendering.',
 'LOW', 'WARNING',
 '["Set oozie.service.PurgeService.older.than=30 (days)", "Set oozie.service.PurgeService.purge.interval=3600 (1 hour)", "Monitor Oozie DB size and query performance", "Archive completed jobs before purging if audit trail needed"]',
 '{"type": "always"}'),

-- ============================================================
-- SOLR DEEP
-- ============================================================

('SOLR-SHARDING', 'Solr collection sharding strategy',
 'Shard count must match index size and query throughput requirements.',
 'Performance', 'Solr', 'Solr',
 '["Slow queries or unbalanced shards"]', '["Default single shard"]', '["Solr"]',
 'Single shard limits query throughput to one server. Too many shards add coordination overhead.',
 'MEDIUM', 'WARNING',
 '["Rule of thumb: 1 shard per 20-50GB of index data", "For Ranger audit: 2-4 shards with RF=2", "For Atlas search: shards based on entity count", "Monitor shard sizes — rebalance when >50GB variation"]',
 '{"type": "always"}'),

('SOLR-COMMIT-STRATEGY', 'Solr commit and auto-commit configuration',
 'Hard commits control when data becomes visible. Too frequent wastes I/O; too infrequent delays visibility.',
 'Performance', 'Solr', 'Solr',
 '["Index lag or excessive I/O"]', '["Default commit settings"]', '["Solr"]',
 'Auto-commit every 100ms generates excessive I/O. Every 5 minutes delays search visibility.',
 'LOW', 'INFO',
 '["Set autoCommit maxTime=30000 (30s) for near-real-time search", "Set autoSoftCommit maxTime=5000 (5s) for fast visibility without fsync", "Hard commit flushes to disk (durable). Soft commit makes visible (fast)", "For audit logs: autoCommit every 60s is usually sufficient"]',
 '{"type": "always"}'),

-- ============================================================
-- CLOUD PATTERNS
-- ============================================================

('CLOUD-OBJECT-STORE-CONSISTENCY', 'Cloud object store consistency model',
 'Cloud object stores (S3, ADLS, GCS) have different consistency models than HDFS.',
 'Architecture', 'Cloud', 'HDFS',
 '["Data visibility delays", "Stale reads"]', '["Assuming HDFS consistency on cloud storage"]', '["Data"]',
 'S3 now offers strong consistency, but Azure Blob has eventual consistency for certain operations.',
 'MEDIUM', 'WARNING',
 '["Understand consistency model of your cloud storage provider", "S3: strong read-after-write consistency (since Dec 2020)", "ADLS Gen2: strong consistency for all operations", "Use S3Guard (DynamoDB-backed) only if on pre-2020 S3 behavior", "Test: write then immediate read to verify consistency"]',
 '{"type": "always"}'),

('CLOUD-EGRESS-COST', 'Cloud egress cost awareness',
 'Cloud data egress charges can be significant for cross-region or internet-facing workloads.',
 'Operations', 'Cloud', 'Platform',
 '["Unexpected cloud bills"]', '["Egress cost not monitored"]', '["Data"]',
 'Unmonitored cross-region replication or large query result sets can generate thousands in egress fees.',
 'MEDIUM', 'WARNING',
 '["Keep compute and storage in the same region to avoid cross-region egress", "Use VPC endpoints for S3/ADLS access (free within region)", "Monitor data transfer costs in cloud billing dashboard", "Compress data before cross-region transfer"]',
 '{"type": "always"}'),

('CLOUD-SPOT-BATCH', 'Spot/preemptible instances for batch workloads',
 'Batch workloads (Spark, MapReduce) can use spot instances for 60-90% cost savings.',
 'Operations', 'Cloud', 'Yarn',
 '["Wasted cloud budget"]', '["On-demand for all workloads"]', '["Yarn"]',
 'Running batch jobs on on-demand instances wastes budget when spot instances are 70% cheaper.',
 'LOW', 'INFO',
 '["Use spot/preemptible instances for YARN NodeManagers running batch workloads", "Keep master nodes (NN, RM, HBase Master) on on-demand/reserved", "Configure Spark to handle spot termination: spark.dynamicAllocation with maxExecutors", "Mix on-demand (30%) + spot (70%) for fault tolerance"]',
 '{"type": "always"}'),

-- ============================================================
-- OPERATIONAL MATURITY
-- ============================================================

('MATURITY-TOIL-REDUCTION', 'Operational toil reduction',
 'Repetitive manual tasks should be automated to free operator time for improvements.',
 'Operations', 'Process', 'Platform',
 '["Operator burnout", "Human error"]', '["High manual toil"]', '["Servers"]',
 'If operators spend >50% of time on repetitive tasks, they have no capacity for improvements.',
 'LOW', 'WARNING',
 '["Identify top 5 most time-consuming manual operations", "Automate: user onboarding, cluster scaling, backup verification, certificate renewal", "Track toil percentage: target <30% of operator time", "Use Ambari/Ansible/Oozie for task automation"]',
 '{"type": "always"}'),

('MATURITY-GAME-DAY', 'Failure simulation (game days)',
 'Scheduled failure simulations build muscle memory for incident response.',
 'Operations', 'Process', 'Platform',
 '["Slow or wrong incident response"]', '["No failure practice"]', '["Servers"]',
 'Teams that practice failures respond 3-5x faster than those who dont.',
 'LOW', 'INFO',
 '["Schedule quarterly game days: simulate NameNode failure, ZK quorum loss, disk failure", "Rotate who responds — dont let one person become the hero", "Time the response: detection → diagnosis → recovery → validation", "Document lessons and update runbooks after each game day"]',
 '{"type": "always"}'),

('MATURITY-BLAMELESS-CULTURE', 'Blameless incident culture',
 'Blameless post-mortems encourage reporting and learning instead of hiding mistakes.',
 'Operations', 'Process', 'Platform',
 '["Unreported incidents", "Repeated failures"]', '["Blame culture"]', '["Servers"]',
 'When people are blamed, they hide incidents and near-misses. The organization learns nothing.',
 'LOW', 'INFO',
 '["Focus post-mortems on systems, not people: what failed vs who failed", "Celebrate near-miss reports as learning opportunities", "Share post-mortems openly across the organization", "Track: number of near-misses reported (more = better culture)"]',
 '{"type": "always"}'),

('MATURITY-KNOWLEDGE-SHARING', 'Knowledge sharing and cross-training',
 'Critical operational knowledge should not reside in a single person (bus factor).',
 'Operations', 'Process', 'Platform',
 '["Bus factor = 1"]', '["Tribal knowledge"]', '["Servers"]',
 'If the only person who knows how to restore HBase goes on vacation during an outage, recovery takes 10x longer.',
 'MEDIUM', 'WARNING',
 '["Ensure bus factor >= 2 for every critical system", "Cross-train team members on: HDFS recovery, HBase operations, Kafka management", "Maintain written runbooks for all critical procedures", "Rotate on-call across all team members"]',
 '{"type": "always"}');
