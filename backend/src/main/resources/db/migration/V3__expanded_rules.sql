-- Expanded recommendation rules covering benchmark results, JMX metrics,
-- system metrics, service health, HDFS reports, YARN queues, SSL certs,
-- kernel parameters, config drift, and alert analysis.

INSERT INTO recommendation_rules (code, title, description, category, subcategory, component, threat, vulnerability, asset, impact, default_likelihood, default_severity, recommendations_text, condition) VALUES

-- ============================================================
-- BENCHMARKS > CPU
-- ============================================================

('BENCH-CPU-STEAL', 'High CPU steal time',
 'CPU steal time indicates the hypervisor is taking CPU cycles from this VM. Above 5% degrades Hadoop performance significantly.',
 'Performance', 'CPU', 'OS',
 '["Performance degradation", "Unpredictable latency"]',
 '["Shared hypervisor resources", "VM overcommit"]',
 '["Servers", "Yarn", "HDFS"]',
 'High steal time causes unpredictable job execution times and can lead to TaskTracker/NodeManager timeouts.',
 'HIGH', 'CRITICAL',
 '["Contact infrastructure team to reduce VM density on the hypervisor", "Request dedicated CPU cores (pinning)", "Monitor steal time trend and correlate with job failures"]',
 '{"type": "threshold_check", "path": "benchmarks.cpu.steal_time.percent", "threshold": 5, "direction": "above"}'),

('BENCH-CPU-IOWAIT', 'High CPU I/O wait',
 'High iowait indicates CPUs are idle waiting for disk I/O. This is often a bottleneck for data-intensive Hadoop workloads.',
 'Performance', 'CPU', 'OS',
 '["Performance degradation"]',
 '["Slow storage subsystem", "Insufficient I/O bandwidth"]',
 '["Servers", "HDFS"]',
 'High iowait reduces effective CPU throughput and slows MapReduce/Spark jobs.',
 'MEDIUM', 'WARNING',
 '["Investigate disk I/O bottlenecks", "Consider SSD storage for hot data", "Check for disk errors with SMART", "Review I/O scheduler configuration"]',
 '{"type": "threshold_check", "path": "benchmarks.cpu.iowait.percent", "threshold": 15, "direction": "above"}'),

('BENCH-CPU-FREQ-SCALING', 'CPU frequency scaling active',
 'CPU frequency scaling (power saving) can reduce Hadoop performance. Hadoop nodes should run at maximum frequency.',
 'Performance', 'CPU', 'OS',
 '["Performance degradation"]',
 '["Power management reducing CPU frequency"]',
 '["Servers"]',
 'CPU frequency below 90% of maximum indicates power scaling is active, reducing computational throughput.',
 'LOW', 'WARNING',
 '["Set CPU governor to performance mode: cpupower frequency-set -g performance", "Disable CPU frequency scaling in BIOS/UEFI", "Verify with: cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor"]',
 '{"type": "threshold_check", "path": "benchmarks.cpu.cpu_frequency.scaling_ratio", "threshold": 0.9, "direction": "below"}'),

-- ============================================================
-- BENCHMARKS > MEMORY
-- ============================================================

('BENCH-MEM-USAGE', 'High memory utilization',
 'Memory utilization above 90% can lead to OOM kills of Java processes and degraded cluster performance.',
 'Performance', 'Memory', 'OS',
 '["Service interruption", "Data loss"]',
 '["Insufficient memory capacity"]',
 '["Servers", "Yarn", "HDFS"]',
 'When system memory is near capacity, the OOM killer may terminate critical Hadoop processes.',
 'HIGH', 'CRITICAL',
 '["Add more RAM to the node", "Reduce Yarn container memory allocations", "Review Java heap sizes for all services", "Check for memory leaks in application code"]',
 '{"type": "threshold_check", "path": "benchmarks.memory.system_memory.used_percent", "threshold": 90, "direction": "above"}'),

('BENCH-MEM-SWAP', 'Active swap usage detected',
 'Any swap usage on a Hadoop node indicates memory pressure and severely degrades performance.',
 'Performance', 'Memory', 'OS',
 '["Severe performance degradation"]',
 '["Insufficient memory", "High swappiness setting"]',
 '["Servers", "Yarn", "HDFS"]',
 'Swapping causes 100-1000x latency increase for memory accesses, crippling Hadoop performance.',
 'MEDIUM', 'CRITICAL',
 '["Set vm.swappiness=1 (not 0, to avoid OOM kills)", "Add more RAM if swap is consistently used", "Review and reduce Java heap allocations", "Check for runaway processes consuming memory"]',
 '{"type": "threshold_check", "path": "benchmarks.memory.swap_analysis.percent_used", "threshold": 1, "direction": "above"}'),

('BENCH-MEM-SWAPPINESS', 'Swappiness too high for Hadoop',
 'vm.swappiness should be set to 1 for Hadoop nodes. Default value of 60 causes excessive swapping.',
 'Performance', 'Memory', 'OS',
 '["Performance degradation"]',
 '["Default kernel settings not tuned for Hadoop"]',
 '["Servers"]',
 'High swappiness causes the kernel to prefer swapping over dropping page cache, degrading Hadoop I/O.',
 'HIGH', 'WARNING',
 '["Set vm.swappiness=1 in /etc/sysctl.conf", "Apply immediately: sysctl vm.swappiness=1", "Never set to 0 as it can trigger OOM kills"]',
 '{"type": "range_check", "path": "benchmarks.memory.swap_analysis.swappiness", "min": 0, "max": 10}'),

-- ============================================================
-- BENCHMARKS > DISK
-- ============================================================

('BENCH-DISK-WRITE', 'Low sequential write throughput',
 'Sequential write throughput below 100 MB/s indicates slow storage or misconfigured disk subsystem.',
 'Performance', 'Disk', 'HDFS',
 '["Performance degradation", "Slow data ingestion"]',
 '["Slow disk subsystem", "RAID misconfiguration"]',
 '["HDFS", "Servers"]',
 'Low disk write speed directly impacts HDFS write performance, replication speed, and MapReduce shuffle.',
 'MEDIUM', 'WARNING',
 '["Check disk health with SMART tools", "Verify RAID configuration and write policy", "Consider SSD or NVMe for faster storage", "Check I/O scheduler: deadline or noop for SSDs"]',
 '{"type": "threshold_check", "path": "benchmarks.disk.sequential_write.throughput_mb_per_second", "threshold": 100, "direction": "below"}'),

('BENCH-DISK-LATENCY', 'High disk write latency',
 'Disk write latency above 5ms (p95) indicates storage performance issues.',
 'Performance', 'Disk', 'HDFS',
 '["Performance degradation"]',
 '["Slow storage subsystem"]',
 '["HDFS", "Servers"]',
 'High disk latency impacts HDFS block writes, NameNode edit log sync, and ZooKeeper transaction log.',
 'HIGH', 'CRITICAL',
 '["Check disk health with smartctl", "Replace failing disks immediately", "Consider battery-backed write cache on RAID controller", "Use deadline I/O scheduler for data disks"]',
 '{"type": "threshold_check", "path": "benchmarks.disk.disk_latency.p95_ms", "threshold": 5, "direction": "above"}'),

('BENCH-DISK-USAGE', 'Disk space nearly full',
 'Disk usage above 85% on data partitions triggers HDFS issues and can halt writes.',
 'Performance', 'Disk', 'HDFS',
 '["Service interruption", "Data loss"]',
 '["Insufficient disk capacity"]',
 '["HDFS", "Servers"]',
 'When disk usage exceeds 85%, HDFS may start decommissioning data or DataNodes become unhealthy.',
 'HIGH', 'CRITICAL',
 '["Add more disk capacity", "Run HDFS balancer to distribute data", "Delete old snapshots and trash", "Review data retention policies"]',
 '{"type": "threshold_check", "path": "metrics.system.disk_usage./.percent", "threshold": 85, "direction": "above"}'),

('BENCH-DISK-NOATIME', 'Data disks missing noatime mount option',
 'Data disks should be mounted with noatime to avoid unnecessary metadata writes on every read.',
 'Performance', 'Disk', 'HDFS',
 '["Performance degradation"]',
 '["Default mount options not optimized"]',
 '["HDFS", "Servers"]',
 'Without noatime, every file read updates the access time, causing extra disk writes and inode contention.',
 'LOW', 'INFO',
 '["Add noatime to mount options in /etc/fstab for data partitions", "Remount without reboot: mount -o remount,noatime /data"]',
 '{"type": "always"}'),

-- ============================================================
-- BENCHMARKS > NETWORK
-- ============================================================

('BENCH-NET-ERRORS', 'Network interface errors detected',
 'Network errors or dropped packets indicate cabling, NIC, or switch issues.',
 'Architecture', 'Network', 'Network',
 '["Data corruption", "Performance degradation"]',
 '["Faulty network hardware", "Switch misconfiguration"]',
 '["Servers", "HDFS"]',
 'Network errors cause HDFS block checksum failures, RPC timeouts, and ZooKeeper session expirations.',
 'MEDIUM', 'CRITICAL',
 '["Check physical cabling and connectors", "Replace faulty NIC if errors persist", "Check switch port error counters", "Verify NIC driver is up to date"]',
 '{"type": "always"}'),

('BENCH-NET-MTU', 'Suboptimal MTU configuration',
 'Network interfaces should use jumbo frames (MTU 9000) for intra-cluster traffic if supported.',
 'Architecture', 'Network', 'Network',
 '["Performance degradation"]',
 '["Default MTU limiting throughput"]',
 '["Servers"]',
 'Standard MTU (1500) causes more packet processing overhead. Jumbo frames reduce CPU usage for network I/O.',
 'LOW', 'INFO',
 '["Verify all switches support jumbo frames", "Set MTU to 9000 on cluster-facing interfaces", "Test with ping -M do -s 8972 between nodes"]',
 '{"type": "always"}'),

-- ============================================================
-- JMX METRICS > HDFS
-- ============================================================

('JMX-HDFS-MISSING-BLOCKS', 'HDFS missing blocks detected',
 'Missing blocks indicate data loss. Even one missing block is critical.',
 'Performance', 'HDFS', 'HDFS',
 '["Data loss"]',
 '["Disk failures", "DataNode unavailability"]',
 '["HDFS", "Data"]',
 'Missing blocks mean data has been permanently lost unless backups exist.',
 'HIGH', 'CRITICAL',
 '["Check DataNode health and disk status", "Run hdfs fsck / to identify affected files", "Restore from backup if available", "Increase replication factor for critical data"]',
 '{"type": "threshold_check", "path": "jmx_metrics.namenode.missing_blocks", "threshold": 0, "direction": "above"}'),

('JMX-HDFS-UNDER-REPLICATED', 'HDFS under-replicated blocks',
 'Under-replicated blocks are at higher risk of data loss if another disk/node fails.',
 'Performance', 'HDFS', 'HDFS',
 '["Potential data loss"]',
 '["Insufficient DataNode capacity", "Recent node failures"]',
 '["HDFS", "Data"]',
 'Under-replicated blocks have fewer copies than the configured replication factor.',
 'MEDIUM', 'WARNING',
 '["Check if all DataNodes are running and healthy", "Run hdfs fsck / to list under-replicated files", "Ensure sufficient disk space for replication", "Run hdfs balancer if data is skewed"]',
 '{"type": "threshold_check", "path": "jmx_metrics.namenode.under_replicated_blocks", "threshold": 100, "direction": "above"}'),

('JMX-HDFS-HEAP', 'NameNode heap usage high',
 'NameNode heap above 80% can cause long GC pauses and eventually OOM.',
 'Performance', 'HDFS', 'HDFS',
 '["Service interruption"]',
 '["Insufficient NameNode heap", "Too many small files"]',
 '["HDFS"]',
 'NameNode OOM causes cluster-wide HDFS outage. High heap usage indicates too many files/blocks.',
 'HIGH', 'CRITICAL',
 '["Increase NameNode heap size (-Xmx)", "Reduce number of small files (use HAR, concatenation)", "Enable NameNode Federation for very large clusters", "Monitor with JMX and set alerts at 70%"]',
 '{"type": "threshold_check", "path": "jmx_metrics.namenode.heap_used_mb", "threshold": 0, "direction": "above", "absent_triggers": false}'),

('JMX-HDFS-CAPACITY', 'HDFS capacity usage high',
 'HDFS usage above 80% of configured capacity may trigger issues.',
 'Performance', 'HDFS', 'HDFS',
 '["Service degradation", "Write failures"]',
 '["Insufficient storage capacity"]',
 '["HDFS", "Data"]',
 'When HDFS is nearly full, new writes fail and replication cannot complete.',
 'HIGH', 'CRITICAL',
 '["Add more DataNodes or disks", "Delete old data and empty trash: hdfs dfs -expunge", "Run hdfs balancer to distribute data evenly", "Review data retention policies"]',
 '{"type": "always"}'),

('JMX-HDFS-VOLUME-FAILURES', 'DataNode volume failures',
 'DataNode reporting disk volume failures. Failed volumes reduce storage capacity and data availability.',
 'Performance', 'HDFS', 'HDFS',
 '["Data loss", "Performance degradation"]',
 '["Disk hardware failure"]',
 '["HDFS", "Servers"]',
 'Volume failures reduce available storage and may cause under-replication.',
 'HIGH', 'CRITICAL',
 '["Replace failed disks immediately", "Check SMART status of all disks", "Review dfs.datanode.failed.volumes.tolerated setting", "Monitor disk health proactively"]',
 '{"type": "always"}'),

-- ============================================================
-- JMX METRICS > YARN
-- ============================================================

('JMX-YARN-PENDING', 'YARN applications pending',
 'Large number of pending applications indicates resource exhaustion.',
 'Performance', 'Yarn', 'Yarn',
 '["Performance degradation", "SLA breach"]',
 '["Insufficient cluster resources", "Queue misconfiguration"]',
 '["Yarn"]',
 'Pending applications wait for resources, causing job delays and potential SLA violations.',
 'MEDIUM', 'WARNING',
 '["Add more NodeManagers for capacity", "Review queue configurations and capacity allocation", "Check for long-running applications blocking resources", "Enable preemption if not already configured"]',
 '{"type": "threshold_check", "path": "jmx_metrics.resourcemanager.apps_pending", "threshold": 10, "direction": "above"}'),

('JMX-YARN-DEAD-NM', 'Unhealthy or lost NodeManagers',
 'NodeManagers not reporting to ResourceManager reduce available cluster capacity.',
 'Architecture', 'High Availability', 'Yarn',
 '["Reduced capacity", "Job failures"]',
 '["Node failure", "Network partition"]',
 '["Yarn", "Servers"]',
 'Lost NodeManagers cause running containers to be killed and reduce available resources.',
 'HIGH', 'CRITICAL',
 '["Check NodeManager process status on affected hosts", "Review NodeManager logs for errors", "Check network connectivity between RM and NMs", "Restart unhealthy NodeManagers"]',
 '{"type": "always"}'),

-- ============================================================
-- HDFS REPORT
-- ============================================================

('HDFS-DEAD-DN', 'Dead DataNodes detected',
 'Dead DataNodes cannot serve data and trigger under-replication.',
 'Architecture', 'High Availability', 'HDFS',
 '["Data unavailability", "Performance degradation"]',
 '["Node failure", "Network issues"]',
 '["HDFS", "Data"]',
 'Dead DataNodes reduce read parallelism and may cause under-replicated blocks.',
 'HIGH', 'CRITICAL',
 '["Check DataNode process on affected hosts", "Review DataNode logs for errors", "Check disk health on affected nodes", "Restart DataNode if process crashed"]',
 '{"type": "threshold_check", "path": "hdfs_report.dead_datanodes", "threshold": 0, "direction": "above"}'),

-- ============================================================
-- KERNEL PARAMETERS
-- ============================================================

('KERN-SWAPPINESS', 'vm.swappiness too high',
 'Hadoop requires low swappiness to avoid performance degradation from swapping.',
 'Performance', 'OS', 'OS',
 '["Performance degradation"]',
 '["Default kernel tuning"]',
 '["Servers"]',
 'High swappiness causes the kernel to prefer swapping over reclaiming page cache.',
 'HIGH', 'WARNING',
 '["Set vm.swappiness=1 in /etc/sysctl.conf", "Apply: sysctl -p"]',
 '{"type": "range_check", "path": "kernel_params.sysctl.vm.swappiness", "min": 0, "max": 10}'),

('KERN-DIRTY-RATIO', 'vm.dirty_ratio not optimized',
 'dirty_ratio controls the maximum percentage of memory that can be dirty before writes are forced.',
 'Performance', 'OS', 'OS',
 '["Performance degradation", "Write stalls"]',
 '["Default kernel tuning"]',
 '["Servers", "HDFS"]',
 'Too high dirty_ratio can cause long write stalls; too low reduces write coalescing.',
 'LOW', 'INFO',
 '["Set vm.dirty_ratio between 15-30 for Hadoop workloads", "Set vm.dirty_background_ratio between 5-10"]',
 '{"type": "range_check", "path": "kernel_params.sysctl.vm.dirty_ratio", "min": 10, "max": 40}'),

('KERN-FD-LIMIT', 'File descriptor limit too low',
 'Hadoop services require high file descriptor limits. Default 1024 is far too low.',
 'Performance', 'OS', 'OS',
 '["Service failure", "Connection errors"]',
 '["Default OS limits"]',
 '["Servers", "HDFS", "Yarn"]',
 'Low FD limit causes "Too many open files" errors in NameNode, DataNode, and other services.',
 'HIGH', 'CRITICAL',
 '["Set soft/hard nofile limit to 65536+ in /etc/security/limits.conf", "Set fs.file-max to 6553600 in /etc/sysctl.conf", "Verify with: ulimit -n"]',
 '{"type": "threshold_check", "path": "kernel_params.file_descriptors.hard_limit", "threshold": 65536, "direction": "below"}'),

('KERN-SOMAXCONN', 'net.core.somaxconn too low',
 'Maximum socket backlog should be high for Hadoop services handling many connections.',
 'Performance', 'OS', 'OS',
 '["Connection drops", "Service degradation"]',
 '["Default kernel limits"]',
 '["Servers"]',
 'Low somaxconn causes connection refusals under load.',
 'LOW', 'WARNING',
 '["Set net.core.somaxconn=4096 in /etc/sysctl.conf", "Apply: sysctl -p"]',
 '{"type": "range_check", "path": "kernel_params.sysctl.net.core.somaxconn", "min": 1024, "max": 65535}'),

-- ============================================================
-- SSL / TLS CERTIFICATES
-- ============================================================

('SEC-SSL-CERTS', 'SSL certificates configured',
 'HTTPS endpoints should have valid SSL certificates for secure communication.',
 'Security', 'Data Protection', 'Platform',
 '["Information leakage", "Man in the middle attacks"]',
 '["Missing or expired SSL certificates"]',
 '["Servers", "Clients"]',
 'Without valid SSL certificates, all HTTPS communication is vulnerable to interception.',
 'MEDIUM', 'WARNING',
 '["Generate and install SSL certificates for all HTTPS endpoints", "Use a trusted CA or configure custom truststore", "Monitor certificate expiry dates"]',
 '{"type": "list_not_empty", "path": "ssl_certs.certs"}'),

-- ============================================================
-- SERVICE HEALTH
-- ============================================================

('SVC-CRITICAL-ALERTS', 'Active critical alerts',
 'Critical Ambari alerts indicate serious issues requiring immediate attention.',
 'Architecture', NULL, 'Platform',
 '["Service outage", "Data loss"]',
 '["Various component failures"]',
 '["Servers", "HDFS", "Yarn"]',
 'Critical alerts often precede or indicate active service outages.',
 'HIGH', 'CRITICAL',
 '["Review each critical alert in Ambari", "Address root causes immediately", "Check service logs for error details"]',
 '{"type": "list_not_empty", "path": "service_health.active_alerts"}'),

-- ============================================================
-- CONFIG DRIFT
-- ============================================================

('CFG-DRIFT', 'Configuration drift detected',
 'On-disk configuration differs from Ambari desired configuration, indicating manual changes or failed deployments.',
 'Architecture', NULL, 'Ambari',
 '["Inconsistent behavior", "Failed failover"]',
 '["Manual configuration changes", "Failed deployment"]',
 '["Servers", "HDFS", "Yarn"]',
 'Config drift can cause inconsistent behavior across nodes and failover failures.',
 'MEDIUM', 'WARNING',
 '["Review drifted properties in Ambari", "Redeploy configurations from Ambari", "Avoid manual config edits on nodes", "Use Ambari blueprints for consistent deployment"]',
 '{"type": "list_not_empty", "path": "config_drift.drifts"}'),

-- ============================================================
-- SYSTEM METRICS
-- ============================================================

('SYS-LOAD-HIGH', 'System load average too high',
 'Load average significantly exceeding CPU count indicates resource contention.',
 'Performance', 'CPU', 'OS',
 '["Performance degradation", "Job timeouts"]',
 '["Resource contention", "Too many processes"]',
 '["Servers"]',
 'Load average over 2x CPU count causes scheduling delays and increased latency.',
 'MEDIUM', 'WARNING',
 '["Identify high-CPU processes", "Review Yarn container allocation vs available cores", "Check for runaway processes", "Consider adding more nodes to the cluster"]',
 '{"type": "always"}'),

('SYS-NTP-SYNC', 'Time synchronization configured',
 'All cluster nodes must have synchronized clocks for Kerberos, distributed consensus, and log correlation.',
 'Architecture', 'OS', 'OS',
 '["Authentication failures", "Data inconsistency"]',
 '["Missing NTP/chrony configuration"]',
 '["Kerberos", "Servers"]',
 'Clock skew causes Kerberos ticket validation failures and inconsistent timestamps across services.',
 'MEDIUM', 'CRITICAL',
 '["Install and configure chrony or ntpd", "Verify sync with: chronyc tracking or ntpstat", "Ensure NTP traffic is allowed through firewall"]',
 '{"type": "metadata_check", "path": "system_info.ntp_status.source", "operator": "not_equals", "expected": "none"}'),

-- ============================================================
-- YARN QUEUES (detailed)
-- ============================================================

('YARN-SINGLE-QUEUE', 'Only default YARN queue configured',
 'Production clusters should have multiple queues for workload isolation.',
 'Performance', 'Yarn', 'Yarn',
 '["Resource contention", "SLA breach"]',
 '["No workload isolation"]',
 '["Yarn"]',
 'Without queue isolation, a single runaway job can consume all cluster resources.',
 'MEDIUM', 'WARNING',
 '["Create dedicated queues for different workloads (batch, interactive, ETL)", "Configure capacity limits per queue", "Enable queue preemption for priority workloads"]',
 '{"type": "metadata_check", "path": "yarn.queues_count", "operator": "greater_than", "expected": 1}'),

-- ============================================================
-- SECURITY (expanded from bundle data)
-- ============================================================

('SEC-KRB-KDC-REACH', 'KDC reachability',
 'All KDC servers must be reachable from every cluster node for Kerberos authentication.',
 'Security', 'Authentication', 'Kerberos',
 '["Authentication failures"]',
 '["Network connectivity issues to KDC"]',
 '["Kerberos", "Servers"]',
 'If KDC is unreachable, all Kerberos-authenticated operations will fail.',
 'HIGH', 'CRITICAL',
 '["Verify KDC hostname resolution", "Check firewall rules for port 88 (TCP/UDP)", "Ensure redundant KDC servers are configured", "Test with: kinit -V principal"]',
 '{"type": "metadata_absent", "path": "kerberos_status.kdc_reachable"}'),

('SEC-KRB-KEYTABS', 'Kerberos keytabs present',
 'Service keytabs must be present for automated Kerberos authentication.',
 'Security', 'Authentication', 'Kerberos',
 '["Authentication failures"]',
 '["Missing keytab files"]',
 '["Kerberos", "Servers"]',
 'Missing keytabs prevent services from authenticating to KDC.',
 'MEDIUM', 'WARNING',
 '["Regenerate missing keytabs with kadmin", "Verify keytab principals match service configuration", "Ensure proper file permissions (400) on keytab files"]',
 '{"type": "list_not_empty", "path": "kerberos_status.keytab_principals"}');
