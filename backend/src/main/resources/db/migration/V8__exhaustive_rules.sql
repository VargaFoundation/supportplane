-- V8: Exhaustive best-practice rules filling all remaining gaps.
-- Covers: HDFS advanced, YARN advanced, HBase advanced, Hive advanced,
-- ZooKeeper tuning, Kafka advanced, Impala advanced, Kudu advanced,
-- NiFi advanced, Atlas advanced, Ranger advanced,
-- Spark (new), MapReduce (new), Oozie (new), Solr (new),
-- OS/Security advanced, Network advanced, General/Meta.

INSERT INTO recommendation_rules (code, title, description, category, subcategory, component, threat, vulnerability, asset, impact, default_likelihood, default_severity, recommendations_text, condition) VALUES

-- ============================================================
-- HDFS ADVANCED
-- ============================================================

('HDFS-NN-FEDERATION', 'NameNode Federation',
 'For clusters with hundreds of millions of files, NameNode Federation distributes the namespace across multiple NameNodes.',
 'Architecture', 'HDFS', 'HDFS',
 '["NameNode memory exhaustion", "Scalability limit"]',
 '["Single namespace bottleneck"]',
 '["HDFS"]',
 'A single NameNode holds the entire namespace in memory (~150 bytes/block). Beyond ~500M files, heap requirements exceed practical limits.',
 'LOW', 'INFO',
 '["Consider Federation when NameNode heap exceeds 100GB", "Each federated NameNode manages an independent namespace (ViewFS)", "Use ViewFS mount table for transparent client access"]',
 '{"type": "always"}'),

('HDFS-BALANCER', 'HDFS data balancer configuration',
 'The HDFS balancer redistributes data across DataNodes to maintain even disk utilization.',
 'Performance', 'HDFS', 'HDFS',
 '["Uneven disk utilization", "Hotspots on overfull DataNodes"]',
 '["Data skew from node additions or failures"]',
 '["HDFS"]',
 'Unbalanced clusters have hotspots: overloaded DataNodes throttle writes while underused nodes waste capacity.',
 'MEDIUM', 'WARNING',
 '["Schedule the balancer regularly: hdfs balancer -threshold 10", "Set dfs.datanode.balance.bandwidthPerSec=104857600 (100MB/s)", "Run during off-peak hours to minimize impact"]',
 '{"type": "always"}'),

('HDFS-SNAPSHOTS', 'HDFS snapshot policies',
 'HDFS snapshots provide point-in-time read-only copies of directories for disaster recovery.',
 'Architecture', 'HDFS', 'HDFS',
 '["No point-in-time recovery"]',
 '["Snapshots not configured"]',
 '["HDFS", "Data"]',
 'Without snapshots, accidental deletions or corruptions require restoring from external backups.',
 'MEDIUM', 'WARNING',
 '["Enable snapshots on critical directories: hdfs dfsadmin -allowSnapshot /path", "Schedule periodic snapshots via cron or Oozie", "Test snapshot restore procedures regularly"]',
 '{"type": "always"}'),

('HDFS-ERASURE-CODING', 'HDFS erasure coding for cold data',
 'Erasure coding reduces storage overhead from 3x to ~1.5x while maintaining fault tolerance.',
 'Performance', 'HDFS', 'HDFS',
 '["Storage cost"]', '["3x replication for cold data"]', '["HDFS"]',
 'EC halves storage requirements for cold data at the cost of slightly higher CPU.',
 'LOW', 'INFO',
 '["Apply EC policies on cold/archive directories only", "Use RS-6-3-1024k policy", "HDFS 3.x required"]',
 '{"type": "always"}'),

('HDFS-SAFEMODE', 'HDFS SafeMode monitoring',
 'SafeMode prevents writes while the NameNode loads metadata. Extended SafeMode indicates issues.',
 'Architecture', 'HDFS', 'HDFS',
 '["Write operations blocked"]', '["Missing blocks preventing SafeMode exit"]', '["HDFS"]',
 'NameNode stays in SafeMode until enough blocks report in. Missing blocks can prevent exit.',
 'HIGH', 'CRITICAL',
 '["Monitor: hdfs dfsadmin -safemode get", "Check missing blocks: hdfs fsck /", "Tune dfs.namenode.safemode.threshold-pct if needed"]',
 '{"type": "always"}'),

('HDFS-QUOTAS', 'HDFS storage and namespace quotas',
 'Quotas prevent individual users or applications from consuming disproportionate resources.',
 'Architecture', 'HDFS', 'HDFS',
 '["Uncontrolled storage growth"]', '["No quota enforcement"]', '["HDFS"]',
 'A single runaway job can fill the cluster or create millions of small files.',
 'MEDIUM', 'WARNING',
 '["Set space quotas: hdfs dfsadmin -setSpaceQuota 1T /user/project", "Set namespace quotas to limit file count", "Create quota policies as part of onboarding"]',
 '{"type": "always"}'),

('HDFS-CHECKPOINT', 'HDFS checkpoint configuration',
 'Checkpointing merges edit logs into fsimage to reduce NameNode startup time.',
 'Architecture', 'HDFS', 'HDFS',
 '["Slow NameNode restart"]', '["Checkpoint interval too long"]', '["HDFS"]',
 'Without regular checkpoints, edit logs grow indefinitely, making restarts extremely slow.',
 'LOW', 'WARNING',
 '["Set dfs.namenode.checkpoint.period=3600", "In HA, Standby handles checkpointing automatically", "Monitor edit log size"]',
 '{"type": "always"}'),

('HDFS-WEBHDFS-SEC', 'WebHDFS security',
 'WebHDFS should use authentication and SSL when exposed to networks.',
 'Security', 'Data Protection', 'HDFS',
 '["Unauthorized data access"]', '["WebHDFS without authentication"]', '["HDFS", "Data"]',
 'Unauthenticated WebHDFS allows any HTTP client to read/write data.',
 'MEDIUM', 'CRITICAL',
 '["Enable Kerberos for WebHDFS", "Set dfs.http.policy=HTTPS_ONLY", "Use Knox as gateway"]',
 '{"type": "always"}'),

-- ============================================================
-- YARN ADVANCED
-- ============================================================

('YARN-TIMELINE-SVC', 'YARN Timeline Service v2',
 'Timeline Service provides application history and metrics.',
 'Architecture', 'Yarn', 'Yarn',
 '["No application history"]', '["Timeline Service not configured"]', '["Yarn"]',
 'History is lost after ResourceManager restart without Timeline Service.',
 'LOW', 'WARNING',
 '["Enable Timeline Service v2 (HBase-backed)", "Set yarn.timeline-service.enabled=true", "Configure version 2.0f"]',
 '{"type": "always"}'),

('YARN-NODE-LABELS', 'YARN node labels',
 'Node labels allow scheduling workloads on dedicated node groups (GPU, SSD).',
 'Performance', 'Yarn', 'Yarn',
 '["Resource contention"]', '["No workload segregation"]', '["Yarn"]',
 'Without labels, GPU jobs may land on non-GPU nodes.',
 'LOW', 'INFO',
 '["Set yarn.node-labels.enabled=true", "Assign labels: yarn rmadmin -addToClusterNodeLabels", "Map labels to queues"]',
 '{"type": "always"}'),

('YARN-SHUFFLE-SVC', 'YARN shuffle service',
 'Shuffle service is required for MapReduce and Spark.',
 'Performance', 'Yarn', 'Yarn',
 '["Shuffle failures"]', '["Shuffle service not configured"]', '["Yarn"]',
 'Without it, reducers cannot fetch map output.',
 'MEDIUM', 'WARNING',
 '["Set yarn.nodemanager.aux-services=mapreduce_shuffle", "Add spark_shuffle for Spark", "Restart NodeManagers"]',
 '{"type": "always"}'),

('YARN-RESOURCE-CALC', 'YARN DominantResourceCalculator',
 'DominantResourceCalculator considers both memory and CPU in scheduling.',
 'Performance', 'Yarn', 'Yarn',
 '["CPU overcommit"]', '["Default calculator ignores CPU"]', '["Yarn"]',
 'Without it, containers get memory but CPU is uncontrolled.',
 'MEDIUM', 'WARNING',
 '["Set yarn.scheduler.capacity.resource-calculator=DominantResourceCalculator", "Required when using CGroups"]',
 '{"type": "always"}'),

-- ============================================================
-- HBASE ADVANCED
-- ============================================================

('HBASE-COMPRESSION', 'HBase table compression',
 'Tables should use Snappy or ZSTD compression to reduce I/O and storage.',
 'Performance', 'HBase', 'HBase',
 '["Excessive disk I/O"]', '["No compression"]', '["HBase", "HDFS"]',
 'Uncompressed tables use 2-5x more storage.',
 'MEDIUM', 'WARNING',
 '["Set COMPRESSION=>SNAPPY per column family", "Use ZSTD for cold data", "Applied on next major compaction"]',
 '{"type": "always"}'),

('HBASE-BLOOM-FILTERS', 'HBase bloom filters',
 'Bloom filters reduce unnecessary disk reads for point lookups.',
 'Performance', 'HBase', 'HBase',
 '["Excessive disk reads"]', '["Bloom filters not enabled"]', '["HBase"]',
 'Without blooms, every Get scans all StoreFiles.',
 'LOW', 'WARNING',
 '["Set BLOOMFILTER=>ROW for point lookups", "BLOOMFILTER=>ROWCOL for column-specific lookups", "Adds ~2% overhead, reduces reads 10-100x"]',
 '{"type": "always"}'),

('HBASE-REPLICATION-PEER', 'HBase cross-cluster replication',
 'Replication provides disaster recovery for HBase data.',
 'Architecture', 'HBase', 'HBase',
 '["No DR for HBase"]', '["Replication not configured"]', '["HBase", "Data"]',
 'Without replication, cluster outage means complete HBase unavailability.',
 'LOW', 'INFO',
 '["Set hbase.replication=true", "Add peers: add_peer peer_id, CLUSTER_KEY", "Set REPLICATION_SCOPE=>1 on column families"]',
 '{"type": "always"}'),

('HBASE-SNAPSHOTS-BP', 'HBase table snapshots',
 'Snapshots provide instant point-in-time copies without data copying.',
 'Architecture', 'HBase', 'HBase',
 '["No point-in-time recovery"]', '["No snapshot policy"]', '["HBase", "Data"]',
 'Recovery without snapshots requires full restore from backup (hours).',
 'MEDIUM', 'WARNING',
 '["Schedule snapshots: snapshot table, name", "Export off-cluster: ExportSnapshot", "Test restore procedure regularly"]',
 '{"type": "always"}'),

('HBASE-RPC-HANDLERS', 'HBase RPC handler count',
 'Default 30 handlers is too low for heavy workloads.',
 'Performance', 'HBase', 'HBase',
 '["Request queuing"]', '["Insufficient handler threads"]', '["HBase"]',
 'Full handler pool causes latency spikes and timeouts.',
 'MEDIUM', 'WARNING',
 '["Set hbase.regionserver.handler.count=100-200", "Monitor handler busy percentage via JMX"]',
 '{"type": "always"}'),

('HBASE-WAL-CONFIG', 'HBase WAL configuration',
 'WAL should be on a dedicated fast disk for write performance.',
 'Performance', 'HBase', 'HBase',
 '["Write latency"]', '["WAL sharing disk with data"]', '["HBase"]',
 'WAL fsync is the write bottleneck. Shared disk doubles contention.',
 'MEDIUM', 'WARNING',
 '["Place WAL on dedicated SSD: hbase.wal.dir", "Never disable WAL for production data"]',
 '{"type": "always"}'),

-- ============================================================
-- HIVE ADVANCED
-- ============================================================

('HIVE-LLAP', 'Hive LLAP daemon',
 'LLAP provides sub-second queries through persistent daemons and in-memory caching.',
 'Performance', 'Hive', 'Hive',
 '["High interactive query latency"]', '["LLAP not deployed"]', '["Hive"]',
 'Without LLAP, every query incurs Tez AM startup (5-15s).',
 'LOW', 'INFO',
 '["Deploy LLAP daemons with sufficient memory", "Set hive.execution.mode=llap", "Requires Tez engine"]',
 '{"type": "always"}'),

('HIVE-METASTORE-HA', 'Hive Metastore HA',
 'Multiple Metastore instances prevent metadata access disruptions.',
 'Architecture', 'High Availability', 'Hive',
 '["All queries fail"]', '["Single Metastore"]', '["Hive"]',
 'Metastore failure blocks Hive, Spark SQL, and Impala.',
 'MEDIUM', 'WARNING',
 '["Deploy 2+ Metastore instances", "Configure comma-separated thrift URIs", "Use database HA for backend"]',
 '{"type": "threshold_check", "path": "hive_metrics.service_info.metastore_count", "threshold": 1, "direction": "below", "absent_triggers": false}'),

('HIVE-BUCKETING', 'Hive table bucketing',
 'Bucketing enables efficient joins, sampling, and is required for ACID.',
 'Performance', 'Hive', 'Hive',
 '["Slow joins"]', '["Tables not bucketed"]', '["Hive"]',
 'Bucketed tables enable bucket-map-join without shuffle.',
 'LOW', 'INFO',
 '["CLUSTERED BY (col) INTO N BUCKETS", "Set hive.enforce.bucketing=true", "Required for ACID tables"]',
 '{"type": "always"}'),

('HIVE-ATLAS-HOOK', 'Hive Atlas lineage hook',
 'Atlas hook captures table and column-level lineage from Hive.',
 'Architecture', 'Hive', 'Hive',
 '["No lineage tracking"]', '["Atlas hook not configured"]', '["Hive", "Atlas"]',
 'Without the hook, no automatic lineage from Hive operations.',
 'LOW', 'INFO',
 '["Set hive.exec.post.hooks=org.apache.atlas.hive.hook.HiveHook", "Verify events in Atlas lineage graph"]',
 '{"type": "always"}'),

-- ============================================================
-- ZOOKEEPER TUNING
-- ============================================================

('ZK-MAX-CLIENT-CNXNS', 'ZooKeeper maxClientCnxns',
 'Default 60 concurrent connections per IP may be too low for busy clusters.',
 'Performance', 'ZooKeeper', 'ZooKeeper',
 '["Connection refused"]', '["Default limit too low"]', '["ZooKeeper"]',
 'HBase RS and Kafka brokers open many connections.',
 'LOW', 'WARNING',
 '["Set maxClientCnxns=200 in zoo.cfg", "Set to 0 for unlimited (use with caution)"]',
 '{"type": "always"}'),

('ZK-TICK-TIME', 'ZooKeeper tickTime tuning',
 'tickTime affects heartbeats and session timeouts.',
 'Performance', 'ZooKeeper', 'ZooKeeper',
 '["False session expirations"]', '["Tick time not tuned"]', '["ZooKeeper"]',
 'Too low causes false expirations; too high delays failure detection.',
 'LOW', 'INFO',
 '["Default 2000ms for LAN", "Increase to 3000-4000 for WAN", "Session timeout = tickTime * range"]',
 '{"type": "always"}'),

('ZK-JUTE-MAXBUFFER', 'ZooKeeper jute.maxbuffer',
 'Max data per znode. Default 1MB; increase for large configs.',
 'Performance', 'ZooKeeper', 'ZooKeeper',
 '["Large znode write failures"]', '["Default too small"]', '["ZooKeeper"]',
 'Kafka and HBase write large znodes.',
 'LOW', 'INFO',
 '["Set jute.maxbuffer=4194304 (4MB)", "Set same on ZK clients"]',
 '{"type": "always"}'),

-- ============================================================
-- KAFKA ADVANCED
-- ============================================================

('KAFKA-RACK-AWARENESS', 'Kafka rack-aware replica placement',
 'Replicas should be spread across racks for rack-level fault tolerance.',
 'Architecture', 'Kafka', 'Kafka',
 '["Data loss from rack failure"]', '["All replicas on same rack"]', '["Kafka", "Data"]',
 'Without rack awareness, a rack failure loses all partition copies.',
 'MEDIUM', 'WARNING',
 '["Set broker.rack=rack_id in server.properties", "Kafka distributes replicas across racks automatically"]',
 '{"type": "always"}'),

('KAFKA-LOG-COMPACTION', 'Kafka log compaction',
 'Compaction retains latest value per key for changelog topics.',
 'Performance', 'Kafka', 'Kafka',
 '["Stale data accumulation"]', '["Compaction not configured"]', '["Kafka"]',
 'Without it, changelog topics grow indefinitely.',
 'LOW', 'INFO',
 '["Set cleanup.policy=compact on changelog topics", "Set min.cleanable.dirty.ratio=0.5"]',
 '{"type": "always"}'),

('KAFKA-SSL-SASL', 'Kafka SSL/SASL authentication',
 'Production Kafka must encrypt traffic and authenticate clients.',
 'Security', 'Authentication', 'Kafka',
 '["Unauthorized access", "Data interception"]', '["Unencrypted broker communication"]', '["Kafka", "Data"]',
 'Any client can produce/consume without authentication.',
 'HIGH', 'CRITICAL',
 '["Set security.inter.broker.protocol=SASL_SSL", "Configure SASL/GSSAPI for Kerberos", "Set ssl.keystore/truststore"]',
 '{"type": "always"}'),

('KAFKA-ACLS', 'Kafka ACLs',
 'ACLs control topic access. Essential for multi-tenant environments.',
 'Security', 'Authorization', 'Kafka',
 '["Unauthorized topic access"]', '["No ACLs"]', '["Kafka", "Data"]',
 'Without ACLs, any user can read/write any topic.',
 'MEDIUM', 'WARNING',
 '["Set authorizer.class.name=kafka.security.authorizer.AclAuthorizer", "Create per-topic ACLs", "Enable Ranger Kafka plugin"]',
 '{"type": "always"}'),

('KAFKA-EXACTLY-ONCE', 'Kafka exactly-once semantics',
 'Idempotent producers and transactional consumers prevent duplicate processing.',
 'Architecture', 'Kafka', 'Kafka',
 '["Duplicate messages"]', '["At-least-once default"]', '["Kafka", "Data"]',
 'Consumers may process duplicates during rebalances.',
 'LOW', 'INFO',
 '["Set enable.idempotence=true", "Use transactional.id for Kafka Streams", "Requires min.insync.replicas>=2"]',
 '{"type": "always"}'),

-- ============================================================
-- IMPALA ADVANCED
-- ============================================================

('IMPALA-QUERY-TIMEOUT', 'Impala query timeout',
 'Timeouts prevent runaway queries from exhausting resources.',
 'Performance', 'Impala', 'Impala',
 '["Resource exhaustion"]', '["No timeout"]', '["Impala"]',
 'A single query can consume all daemon memory.',
 'MEDIUM', 'WARNING',
 '["Set --idle_query_timeout=3600", "Set --idle_session_timeout=7200", "Configure per-pool timeouts"]',
 '{"type": "always"}'),

('IMPALA-RUNTIME-FILTERS', 'Impala runtime filters',
 'Runtime filters push join predicates to scan side, reducing data by 90%+.',
 'Performance', 'Impala', 'Impala',
 '["Slow joins"]', '["Filters disabled"]', '["Impala"]',
 'Dramatically improves star schema join performance.',
 'LOW', 'INFO',
 '["Ensure RUNTIME_FILTER_MODE=GLOBAL", "Most effective for selective dimension joins"]',
 '{"type": "always"}'),

('IMPALA-STALE-METADATA', 'Impala stale metadata handling',
 'Impala caches metadata aggressively. External changes need explicit refresh.',
 'Performance', 'Impala', 'Impala',
 '["Stale results"]', '["No refresh strategy"]', '["Impala"]',
 'Changes from Hive/Spark not visible until refresh.',
 'MEDIUM', 'WARNING',
 '["Use INVALIDATE METADATA after DDL", "REFRESH after data loads", "Enable --hms_event_polling_interval_s=2"]',
 '{"type": "always"}'),

-- ============================================================
-- KUDU ADVANCED
-- ============================================================

('KUDU-FLUSH-THRESHOLD', 'Kudu flush threshold',
 'Controls memory accumulation before disk flush.',
 'Performance', 'Kudu', 'Kudu',
 '["High memory or write stalls"]', '["Not tuned"]', '["Kudu"]',
 'Too high uses excess memory; too low causes frequent small flushes.',
 'LOW', 'INFO',
 '["Default --flush_threshold_mb=1024", "Increase to 2048 for write-heavy with sufficient memory"]',
 '{"type": "always"}'),

('KUDU-COMPACTION-POLICY', 'Kudu compaction policy',
 'Compaction merges DiskRowSets and applies deltas. Critical for reads.',
 'Performance', 'Kudu', 'Kudu',
 '["Read degradation"]', '["Compaction behind"]', '["Kudu"]',
 'Without timely compaction, reads merge many small files.',
 'LOW', 'INFO',
 '["Set --maintenance_manager_num_threads=4", "Monitor compaction queue via metrics"]',
 '{"type": "always"}'),

-- ============================================================
-- NIFI ADVANCED
-- ============================================================

('NIFI-SENSITIVE-KEY', 'NiFi sensitive properties key',
 'Encrypts passwords in flow configuration.',
 'Security', 'NiFi', 'NiFi',
 '["Credentials exposed"]', '["Default or missing key"]', '["NiFi"]',
 'Without it, passwords stored in plain text in flow.xml.',
 'HIGH', 'CRITICAL',
 '["Set nifi.sensitive.props.key to a strong value", "Back up the key — losing it makes flow unreadable"]',
 '{"type": "always"}'),

('NIFI-FLOW-VERSIONING', 'NiFi flow version control',
 'NiFi Registry enables change tracking and rollback.',
 'Architecture', 'NiFi', 'NiFi',
 '["No change tracking"]', '["No Registry"]', '["NiFi"]',
 'Changes are immediate and irreversible without versioning.',
 'MEDIUM', 'WARNING',
 '["Deploy NiFi Registry", "Version all process groups before deployment"]',
 '{"type": "always"}'),

('NIFI-SITE-TO-SITE', 'NiFi site-to-site security',
 'S2S transfers between NiFi instances must be secured.',
 'Architecture', 'NiFi', 'NiFi',
 '["Insecure inter-cluster data"]', '["S2S not secured"]', '["NiFi"]',
 'Unencrypted transfers vulnerable to interception.',
 'LOW', 'WARNING',
 '["Set nifi.remote.input.secure=true", "Use certificate-based mutual auth"]',
 '{"type": "always"}'),

-- ============================================================
-- ATLAS ADVANCED
-- ============================================================

('ATLAS-CLASSIFICATION', 'Atlas classification propagation',
 'Propagation automatically tags derived data with source classifications.',
 'Architecture', 'Atlas', 'Atlas',
 '["Governance gaps"]', '["Propagation disabled"]', '["Atlas", "Data"]',
 'PII tags on source dont flow to downstream tables.',
 'LOW', 'INFO',
 '["Enable propagation in Atlas config", "Define rules for sensitive classifications"]',
 '{"type": "always"}'),

('ATLAS-HOOK-CONFIG', 'Atlas hook per-service',
 'Each service needs its hook configured for metadata capture.',
 'Architecture', 'Atlas', 'Atlas',
 '["Incomplete catalog"]', '["Hooks missing"]', '["Atlas"]',
 'Missing hooks = invisible metadata changes.',
 'MEDIUM', 'WARNING',
 '["Configure Hive, Kafka, Spark hooks", "Verify events in Atlas Kafka topic"]',
 '{"type": "always"}'),

-- ============================================================
-- RANGER ADVANCED
-- ============================================================

('RANGER-KMS', 'Ranger KMS integration',
 'KMS manages encryption keys for HDFS TDE with audited access.',
 'Security', 'Data Protection', 'Ranger',
 '["Insecure key management"]', '["KMS not deployed"]', '["Ranger", "HDFS", "Data"]',
 'Without KMS, keys may be stored insecurely.',
 'MEDIUM', 'WARNING',
 '["Deploy Ranger KMS alongside HDFS TDE", "Define key access policies"]',
 '{"type": "always"}'),

('RANGER-TAG-POLICIES', 'Ranger tag-based policies',
 'Tag policies apply controls based on classifications rather than resources.',
 'Security', 'Authorization', 'Ranger',
 '["Policy overhead"]', '["Only resource-based policies"]', '["Ranger", "Data"]',
 'Resource policies need per-table rules; tag policies auto-apply.',
 'LOW', 'INFO',
 '["Enable tag sync with Atlas", "Create classification-based policies"]',
 '{"type": "always"}'),

('RANGER-COL-MASKING', 'Ranger column masking and row filtering',
 'Fine-grained data protection without duplicating data.',
 'Security', 'Authorization', 'Ranger',
 '["Sensitive data exposure"]', '["No masking"]', '["Ranger", "Data"]',
 'Users see all columns including SSN, credit card etc.',
 'MEDIUM', 'WARNING',
 '["Configure masking policies for sensitive columns", "Use MASK_SHOW_LAST_4 for partial visibility", "Configure row-level filtering for multi-tenant data"]',
 '{"type": "always"}'),

-- ============================================================
-- SPARK
-- ============================================================

('SPARK-DYNAMIC-ALLOC', 'Spark dynamic allocation',
 'Adjusts executor count based on workload for better resource utilization.',
 'Performance', 'Spark', 'Spark',
 '["Resource waste or starvation"]', '["Fixed executor count"]', '["Spark", "Yarn"]',
 'Fixed allocation wastes resources during idle phases.',
 'MEDIUM', 'WARNING',
 '["Set spark.dynamicAllocation.enabled=true", "Requires external shuffle service"]',
 '{"type": "metadata_check", "path": "spark_metrics.config.dynamic_allocation", "operator": "equals", "expected": "true"}'),

('SPARK-SERIALIZER', 'Spark Kryo serializer',
 'Kryo is 10x faster than Java serializer with smaller output.',
 'Performance', 'Spark', 'Spark',
 '["Slow shuffle"]', '["Default Java serializer"]', '["Spark"]',
 'Java serializer is slow and verbose.',
 'LOW', 'WARNING',
 '["Set spark.serializer=org.apache.spark.serializer.KryoSerializer"]',
 '{"type": "metadata_check", "path": "spark_metrics.config.serializer", "operator": "contains", "expected": "Kryo"}'),

('SPARK-EXECUTOR-MEM', 'Spark executor memory',
 'Default 1g is too low for production. Tune per workload.',
 'Performance', 'Spark', 'Spark',
 '["OOM errors"]', '["Undersized executors"]', '["Spark"]',
 'Undersized executors spill to disk and thrash GC.',
 'MEDIUM', 'WARNING',
 '["Set spark.executor.memory=4g-16g", "Set memoryOverhead=max(384MB, 0.1*executorMemory)"]',
 '{"type": "always"}'),

('SPARK-SHUFFLE-PARTITIONS', 'Spark shuffle partitions',
 'Default 200 is rarely optimal. Target 128-256MB per partition.',
 'Performance', 'Spark', 'Spark',
 '["Overhead or insufficient parallelism"]', '["Default 200 not tuned"]', '["Spark"]',
 'Too many = scheduling overhead. Too few = data skew.',
 'LOW', 'INFO',
 '["Enable AQE: spark.sql.adaptive.enabled=true for auto-tuning", "Or set spark.sql.shuffle.partitions based on data volume"]',
 '{"type": "always"}'),

('SPARK-EVENT-LOG', 'Spark event log',
 'Event logs enable History Server for debugging completed jobs.',
 'Architecture', 'Spark', 'Spark',
 '["Cannot debug completed jobs"]', '["Event logging disabled"]', '["Spark"]',
 'Without logs, job details are lost after completion.',
 'LOW', 'WARNING',
 '["Set spark.eventLog.enabled=true", "Set spark.eventLog.dir=hdfs:///spark-history"]',
 '{"type": "metadata_check", "path": "spark_metrics.config.event_log_enabled", "operator": "equals", "expected": "true"}'),

('SPARK-SPECULATION', 'Spark speculative execution',
 'Re-launches slow tasks to mitigate stragglers.',
 'Performance', 'Spark', 'Spark',
 '["Job slowdown"]', '["Speculation disabled"]', '["Spark"]',
 'A single slow task delays the entire stage.',
 'LOW', 'INFO',
 '["Set spark.speculation=true for heterogeneous clusters", "Disable for non-idempotent outputs"]',
 '{"type": "always"}'),

('SPARK-ADAPTIVE', 'Spark Adaptive Query Execution',
 'AQE optimizes plans at runtime: coalesces partitions, handles skew, converts joins.',
 'Performance', 'Spark', 'Spark',
 '["Suboptimal performance"]', '["AQE not enabled"]', '["Spark"]',
 'The single most impactful Spark 3 performance feature.',
 'MEDIUM', 'WARNING',
 '["Set spark.sql.adaptive.enabled=true", "Set coalescePartitions.enabled=true", "Set skewJoin.enabled=true"]',
 '{"type": "metadata_check", "path": "spark_metrics.config.adaptive_enabled", "operator": "equals", "expected": "true"}'),

-- ============================================================
-- MAPREDUCE
-- ============================================================

('MR-UBER-MODE', 'MapReduce uber mode',
 'Small jobs run in ApplicationMaster JVM, eliminating container overhead.',
 'Performance', 'MapReduce', 'Yarn',
 '["Overhead for small jobs"]', '["Uber disabled"]', '["Yarn"]',
 'Even 1-second jobs incur 10-30s container launch overhead.',
 'LOW', 'INFO',
 '["Set mapreduce.job.ubertask.enable=true", "Set maxmaps=9, maxreduces=1"]',
 '{"type": "always"}'),

('MR-SPECULATIVE-EXEC', 'MapReduce speculative execution',
 'Re-runs slow tasks to avoid stragglers.',
 'Performance', 'MapReduce', 'Yarn',
 '["Job delayed by stragglers"]', '["Speculation disabled"]', '["Yarn"]',
 'Single slow task holds up entire job.',
 'LOW', 'INFO',
 '["Set mapreduce.map.speculative=true", "Set mapreduce.reduce.speculative=true"]',
 '{"type": "always"}'),

-- ============================================================
-- OOZIE
-- ============================================================

('OOZIE-SLA-MONITOR', 'Oozie SLA monitoring',
 'SLA monitoring alerts on missed workflow start/end times.',
 'Architecture', 'Oozie', 'Oozie',
 '["SLA breaches undetected"]', '["No SLA monitoring"]', '["Oozie"]',
 'Delayed ETL discovered only when consumers complain.',
 'MEDIUM', 'WARNING',
 '["Enable SLA service in oozie-site.xml", "Define <sla:info> in workflows", "Configure email alerts"]',
 '{"type": "always"}'),

('OOZIE-SHARED-LIB', 'Oozie shared library',
 'Shared libs provide Hadoop JARs without bundling in each workflow.',
 'Architecture', 'Oozie', 'Oozie',
 '["Workflow failures"]', '["Shared libs outdated"]', '["Oozie"]',
 'Outdated libs cause ClassNotFoundException after upgrades.',
 'LOW', 'WARNING',
 '["Update after upgrades: oozie-setup.sh sharelib upgrade", "Verify: oozie admin -shareliblist"]',
 '{"type": "always"}'),

-- ============================================================
-- SOLR
-- ============================================================

('SOLR-REPLICATION', 'Solr collection replication',
 'Collections need replication >= 2 for high availability.',
 'Architecture', 'High Availability', 'Solr',
 '["Index unavailability"]', '["Single replica"]', '["Solr"]',
 'Node failure makes shard unavailable.',
 'MEDIUM', 'WARNING',
 '["Create with replicationFactor=2+", "RF=3 for Atlas/Ranger audit indexes"]',
 '{"type": "always"}'),

('SOLR-HEAP', 'Solr JVM heap sizing',
 'Heap must match index size. 8-16GB for moderate indexes.',
 'Performance', 'Solr', 'Solr',
 '["OOM or GC pauses"]', '["Suboptimal heap"]', '["Solr"]',
 'Field/filter caches live in heap.',
 'MEDIUM', 'WARNING',
 '["Set heap 8-16GB", "Use G1GC for >8GB", "Consider off-heap docValues"]',
 '{"type": "always"}'),

-- ============================================================
-- OS / SECURITY ADVANCED
-- ============================================================

('OS-SELINUX', 'SELinux configuration',
 'SELinux can interfere with Hadoop. Should be permissive or have custom policies.',
 'Architecture', 'OS', 'OS',
 '["Service failures"]', '["SELinux enforcing without policies"]', '["Servers"]',
 'Blocks file access, port binding without custom policies.',
 'LOW', 'WARNING',
 '["Set SELINUX=permissive in /etc/selinux/config", "Check denials: ausearch -m avc"]',
 '{"type": "always"}'),

('OS-CERT-RENEWAL', 'Certificate expiry monitoring',
 'SSL certs must be renewed before expiry to avoid outages.',
 'Security', 'Data Protection', 'Platform',
 '["Service outage"]', '["No renewal monitoring"]', '["Servers"]',
 'Expired certs cause SSL handshake failures everywhere.',
 'HIGH', 'CRITICAL',
 '["Set alerts at 30 and 7 days before expiry", "Automate renewal with certbot or PKI"]',
 '{"type": "always"}'),

('OS-PASSWORD-POLICY', 'Service account password rotation',
 'Passwords should be rotated on a regular schedule.',
 'Security', 'Access', 'Platform',
 '["Credential compromise"]', '["Static passwords"]', '["Servers"]',
 'Long-lived passwords increase theft window.',
 'LOW', 'WARNING',
 '["Rotate quarterly", "Use Ambari credential store", "Rotate keytabs on same schedule"]',
 '{"type": "always"}'),

-- ============================================================
-- NETWORK ADVANCED
-- ============================================================

('NET-DNS-CACHING', 'DNS caching',
 'Local DNS caching reduces lookup latency for frequent resolutions.',
 'Performance', 'Network', 'Network',
 '["High DNS latency"]', '["No local caching"]', '["Servers"]',
 'Hadoop resolves hostnames frequently. High latency adds up.',
 'LOW', 'INFO',
 '["Install nscd or dnsmasq", "Or maintain /etc/hosts for cluster nodes"]',
 '{"type": "always"}'),

('NET-TCP-KEEPALIVE', 'TCP keepalive settings',
 'Default 2-hour keepalive is too slow for detecting broken connections.',
 'Performance', 'Network', 'Network',
 '["Slow broken connection detection"]', '["Default too long"]', '["Servers"]',
 'Broken connections undetected for hours.',
 'LOW', 'INFO',
 '["Set net.ipv4.tcp_keepalive_time=600", "Set intvl=60, probes=5 in sysctl.conf"]',
 '{"type": "always"}'),

-- ============================================================
-- GENERAL / META
-- ============================================================

('GEN-BACKUP-STRATEGY', 'Cluster backup strategy',
 'Documented and tested backup strategy for disaster recovery.',
 'Architecture', NULL, 'Platform',
 '["Unrecoverable data loss"]', '["No backup strategy"]', '["Data", "HDFS", "HBase"]',
 'Hardware failure, bugs, or human error can cause permanent loss.',
 'HIGH', 'CRITICAL',
 '["HDFS: distcp to secondary cluster/cloud", "HBase: snapshots + ExportSnapshot", "Metastore: database dumps", "Test restores quarterly"]',
 '{"type": "always"}'),

('GEN-DR-PLAN', 'Disaster recovery plan',
 'Documented DR plan with RTO/RPO for business continuity.',
 'Architecture', NULL, 'Platform',
 '["Extended outage"]', '["No DR plan"]', '["Data", "Servers"]',
 'Datacenter event means complete loss without DR.',
 'MEDIUM', 'CRITICAL',
 '["Define RTO and RPO", "Cross-DC replication for critical data", "Document step-by-step recovery", "DR drills annually"]',
 '{"type": "always"}'),

('GEN-MONITORING', 'Monitoring integration',
 'Export metrics to Prometheus/Grafana for proactive issue detection.',
 'Architecture', NULL, 'Platform',
 '["Issues discovered by users"]', '["No monitoring beyond Ambari"]', '["Servers"]',
 'Ambari misses performance degradation and trends.',
 'MEDIUM', 'WARNING',
 '["Export JMX to Prometheus via jmx_exporter", "Build dashboards for key metrics", "Configure PagerDuty/OpsGenie alerts"]',
 '{"type": "always"}'),

('GEN-LOG-ROTATION', 'Service log rotation',
 'All logs need rotation to prevent disk exhaustion.',
 'Architecture', NULL, 'Platform',
 '["Disk full"]', '["No log rotation"]', '["Servers"]',
 'Hadoop generates large volumes that fill data disks.',
 'MEDIUM', 'WARNING',
 '["Configure logrotate: weekly, compress, keep 4 weeks", "Set log4j maxFileSize in all services"]',
 '{"type": "always"}'),

('GEN-CAPACITY-PLAN', 'Capacity planning',
 'Regular projections ensure cluster handles growth without emergency scaling.',
 'Architecture', NULL, 'Platform',
 '["Capacity exhaustion"]', '["No projections"]', '["Servers", "HDFS", "Yarn"]',
 'Growth hits a wall requiring emergency procurement.',
 'LOW', 'WARNING',
 '["Track monthly growth rates", "Project 6-12 months ahead", "Plan 30% headroom above peak"]',
 '{"type": "always"}'),

('GEN-VERSION-COMPAT', 'Service version compatibility',
 'All services should run compatible versions from the same stack.',
 'Architecture', NULL, 'Platform',
 '["Incompatible APIs"]', '["Mixed versions"]', '["Servers"]',
 'Version mismatches cause hard-to-diagnose failures.',
 'MEDIUM', 'WARNING',
 '["Keep all on same stack version", "Test upgrades in staging first"]',
 '{"type": "metadata_check", "path": "platform.versions_up_to_date", "operator": "equals", "expected": true}');
