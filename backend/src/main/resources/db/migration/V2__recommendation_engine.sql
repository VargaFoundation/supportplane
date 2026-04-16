-- Recommendation Engine: rules, audit runs, extended recommendations

-- Configurable recommendation rules (audit finding templates)
CREATE TABLE recommendation_rules (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(100) NOT NULL UNIQUE,
    title                 VARCHAR(500) NOT NULL,
    description           TEXT,
    category              VARCHAR(100) NOT NULL,
    subcategory           VARCHAR(100),
    component             VARCHAR(100) NOT NULL,
    threat                TEXT,
    vulnerability         TEXT,
    asset                 TEXT,
    impact                TEXT,
    default_likelihood    VARCHAR(20) DEFAULT 'MEDIUM',
    default_severity      VARCHAR(20) DEFAULT 'WARNING',
    recommendations_text  TEXT,
    condition             JSONB,
    enabled               BOOLEAN DEFAULT TRUE,
    created_by            BIGINT REFERENCES users(id),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Audit run history
CREATE TABLE audit_runs (
    id                BIGSERIAL PRIMARY KEY,
    cluster_id        BIGINT NOT NULL REFERENCES clusters(id),
    triggered_by      BIGINT REFERENCES users(id),
    status            VARCHAR(50) DEFAULT 'RUNNING',
    rules_evaluated   INTEGER DEFAULT 0,
    findings_count    INTEGER DEFAULT 0,
    summary           JSONB,
    started_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at      TIMESTAMP
);

-- Extend recommendations with structured audit fields
ALTER TABLE recommendations ADD COLUMN rule_id BIGINT REFERENCES recommendation_rules(id);
ALTER TABLE recommendations ADD COLUMN category VARCHAR(100);
ALTER TABLE recommendations ADD COLUMN subcategory VARCHAR(100);
ALTER TABLE recommendations ADD COLUMN component VARCHAR(100);
ALTER TABLE recommendations ADD COLUMN threat TEXT;
ALTER TABLE recommendations ADD COLUMN vulnerability TEXT;
ALTER TABLE recommendations ADD COLUMN asset TEXT;
ALTER TABLE recommendations ADD COLUMN impact TEXT;
ALTER TABLE recommendations ADD COLUMN likelihood VARCHAR(20);
ALTER TABLE recommendations ADD COLUMN risk VARCHAR(20);
ALTER TABLE recommendations ADD COLUMN recommendations_text TEXT;
ALTER TABLE recommendations ADD COLUMN finding_status VARCHAR(20) DEFAULT 'UNKNOWN';
ALTER TABLE recommendations ADD COLUMN evaluation_data JSONB;

-- Indexes
CREATE INDEX idx_rules_category ON recommendation_rules(category);
CREATE INDEX idx_rules_component ON recommendation_rules(component);
CREATE INDEX idx_rules_enabled ON recommendation_rules(enabled);
CREATE INDEX idx_audit_runs_cluster ON audit_runs(cluster_id);
CREATE INDEX idx_recommendations_rule ON recommendations(rule_id);
CREATE INDEX idx_recommendations_category ON recommendations(category);
CREATE INDEX idx_recommendations_component ON recommendations(component);
CREATE INDEX idx_recommendations_finding_status ON recommendations(finding_status);

-- Seed rules from audit.asciidoc

INSERT INTO recommendation_rules (code, title, description, category, subcategory, component, threat, vulnerability, asset, impact, default_likelihood, default_severity, recommendations_text, condition) VALUES

-- Performance > Outdated Versions
('PERF-VERSIONS', 'Outdated versions',
 'Over time, new vulnerabilities will be discovered that can be exploited by low-skilled hackers, leaving your organizations susceptible to attack, data loss, or even fiscal consequences.',
 'Performance', NULL, 'Platform',
 '["Breach"]',
 '["Exposition to known exploits"]',
 '["Data", "Servers"]',
 'Critical data may be stolen or corrupted',
 'LOW', 'CRITICAL',
 '["Check for latest stable version of all platform components", "Apply security patches regularly"]',
 '{"type": "metadata_check", "path": "platform.versions_up_to_date", "operator": "equals", "expected": true}'),

-- Performance > Naming/CNAMES
('PERF-NAMING', 'Naming, aliases and CNAMES',
 'Use CNAMEs to identify your servers to the cluster so that you can rename and renumber your servers without downtime.',
 'Performance', NULL, 'Platform',
 '["Long service downtime"]',
 '["Incapacity of restoring a single server or a service on a single server"]',
 '["Servers"]',
 'Long cluster downtime',
 'HIGH', 'WARNING',
 '["Update DNS with logical name", "Use one logical name per service/role/component"]',
 '{"type": "metadata_check", "path": "network.cnames_configured", "operator": "equals", "expected": true}'),

-- Performance > Auto-start
('PERF-AUTOSTART', 'Auto-start services',
 'Ambari can auto-start Hadoop services, which causes the ambari-agent to attempt re-starting service components in a stopped state without manual effort.',
 'Performance', NULL, 'Ambari',
 '["Interruption of services"]',
 '["Inadequate supervision"]',
 '["Servers"]',
 'More support for cluster admin and cluster operator',
 'LOW', 'WARNING',
 '["Enable auto-start for all the services on Ambari"]',
 '{"type": "metadata_check", "path": "ambari.autostart_enabled", "operator": "equals", "expected": true}'),

-- Performance > HDFS > Small files
('PERF-HDFS-SMALLFILES', 'HDFS small files',
 'A small file is one which is significantly smaller than the HDFS block size. Reading through small files normally causes lots of seeks and lots of hopping from datanode to datanode.',
 'Performance', 'HDFS', 'HDFS',
 '["Failure of information system"]',
 '["Inadequate supervision"]',
 '["HDFS"]',
 'Leaving small files on the cluster may introduce performance issues. Average block size should be close to the configured block size.',
 'LOW', 'INFO',
 '["Use HDFS archive", "Do not use partitioning when data are small", "Use HBase if you have many small data"]',
 '{"type": "metadata_check", "path": "hdfs.small_files_ratio", "operator": "less_than", "expected": 0.3}'),

-- Performance > Yarn > Queues
('PERF-YARN-QUEUES', 'Yarn queues configuration',
 'Queues are the primary method used to manage multiple workloads and provide workload isolation.',
 'Performance', 'Yarn', 'Yarn',
 '["Failure of information system"]',
 '["Uncontrolled use of information system"]',
 '["Yarn"]',
 'Only one queue is defined (the default). New jobs can be blocked if some huge jobs are already running.',
 'MEDIUM', 'WARNING',
 '["Create multiple queues based on usage: batch, interactive, speed", "Bind the jobs to the queues", "If Oozie is used, create a dedicated queue for the launchers"]',
 '{"type": "metadata_check", "path": "yarn.queues_count", "operator": "greater_than", "expected": 1}'),

-- Performance > Yarn > Container isolation
('PERF-YARN-CGROUPS', 'Yarn container isolation (CGroups)',
 'CGroups is a mechanism for aggregating/partitioning sets of tasks into hierarchical groups with specialized behaviour.',
 'Performance', 'Yarn', 'Yarn',
 '["Failure of information system"]',
 '["Uncontrolled use of information system"]',
 '["Yarn", "Servers"]',
 'Without CGroups, containers are not limited in their resource usage and can affect other workloads.',
 'MEDIUM', 'WARNING',
 '["Set yarn.nodemanager.container-executor.class to LinuxContainerExecutor", "Set yarn.nodemanager.linux-container-executor.resources-handler.class to CgroupsLCEResourcesHandler"]',
 '{"type": "metadata_check", "path": "yarn.cgroups_enabled", "operator": "equals", "expected": true}'),

-- Security > Data Protection > TDE
('SEC-DATA-TDE', 'Transparent data encryption (at rest)',
 'HDFS implements transparent, end-to-end encryption. Once configured, data read from and written to special HDFS directories is transparently encrypted and decrypted.',
 'Security', 'Data Protection', 'HDFS',
 '["Information leakage"]',
 '["Inadequate change management", "Lack of access control policy", "Inadequate control of physical access"]',
 '["HDFS", "Data"]',
 'Attacks at the filesystem-level and below are possible. Disks often need to be removed and replaced with human readable residual data.',
 'MEDIUM', 'CRITICAL',
 '["Install and configure Hadoop Key Management Server (KMS)", "Define encryption zones for sensitive data"]',
 '{"type": "metadata_check", "path": "security.encryption.at_rest", "operator": "equals", "expected": true}'),

-- Security > Data Protection > RPC Encryption
('SEC-DATA-RPC', 'RPC encryption (wire)',
 'The data transferred between Hadoop services and clients can be encrypted on the wire.',
 'Security', 'Data Protection', 'HDFS',
 '["Information leakage"]',
 '["Unprotected network connections", "Inadequate control of physical access"]',
 '["Servers", "Clients"]',
 'Communications between Hadoop services and clients are not encrypted and vulnerable to Man in the Middle attacks.',
 'MEDIUM', 'CRITICAL',
 '["Define hadoop.rpc.protection to privacy in core-site.xml to activate data encryption"]',
 '{"type": "metadata_check", "path": "security.encryption.rpc", "operator": "equals", "expected": true}'),

-- Security > Data Protection > Block Transfer Encryption
('SEC-DATA-BLOCK', 'Data encryption on block data transfer',
 'The actual data transfer between the client and a DataNode is over Data Transfer Protocol.',
 'Security', 'Data Protection', 'HDFS',
 '["Information leakage"]',
 '["Unprotected network connections", "Inadequate control of physical access"]',
 '["HDFS"]',
 'Block transfers between HDFS datanodes and clients are vulnerable to Man in the Middle attacks.',
 'MEDIUM', 'CRITICAL',
 '["Set dfs.encrypt.data.transfer to true", "Set dfs.encrypt.data.transfer.algorithm to 3des or rc4", "Set dfs.encrypt.data.transfer.cipher.suites to AES/CTR/NoPadding"]',
 '{"type": "metadata_check", "path": "security.encryption.block_transfer", "operator": "equals", "expected": true}'),

-- Security > Data Protection > HTTP Encryption
('SEC-DATA-HTTP', 'Data encryption on HTTP',
 'Data transfer between Web-console and clients should be protected by using SSL (HTTPS).',
 'Security', 'Data Protection', 'Platform',
 '["Information leakage"]',
 '["Unprotected network connections", "Inadequate control of physical access"]',
 '["HDFS", "Yarn"]',
 'Web consoles are vulnerable to Man in the Middle attacks without HTTPS.',
 'MEDIUM', 'CRITICAL',
 '["Set dfs.http.policy to HTTPS_ONLY in hdfs-site.xml", "Set yarn.http.policy to HTTPS_ONLY in yarn-site.xml", "Set mapreduce.jobhistory.http.policy to HTTPS_ONLY in mapred-site.xml"]',
 '{"type": "metadata_check", "path": "security.encryption.http", "operator": "equals", "expected": true}'),

-- Security > Authentication > Kerberos
('SEC-AUTH-KERBEROS', 'Kerberos authentication',
 'Hadoop uses Kerberos as the basis for strong authentication and identity propagation for both user and services.',
 'Security', 'Authentication', 'Kerberos',
 '["Information leakage"]',
 '["Unprotected network connections", "Inadequate control of physical access"]',
 '["HDFS", "Yarn", "Servers"]',
 'No user identity verification in the cluster.',
 'MEDIUM', 'CRITICAL',
 '["Set hadoop.security.authentication to kerberos", "Set hadoop.security.authorization to true"]',
 '{"type": "metadata_check", "path": "security.kerberos.enabled", "operator": "equals", "expected": true}'),

-- Security > Authentication > SPNEGO
('SEC-AUTH-SPNEGO', 'SPNEGO web authentication',
 'By default, access to the HTTP-based services and UIs for the cluster are not configured to require authentication.',
 'Security', 'Authentication', 'Kerberos',
 '["Information leakage", "Theft", "Vandalism", "Industrial espionage", "Malicious code", "Access by unauthorized persons"]',
 '["Lack of systems for identification and authentication"]',
 '["Servers", "Clients"]',
 'No user identity verification for web UIs in the cluster.',
 'MEDIUM', 'CRITICAL',
 '["Set hadoop.http.authentication.type to kerberos", "Set hadoop.http.authentication.simple.anonymous.allowed to false", "Configure SPNEGO keytab and principal"]',
 '{"type": "metadata_check", "path": "security.spnego.enabled", "operator": "equals", "expected": true}'),

-- Security > Authorization > Service Level
('SEC-AUTHZ-SERVICE', 'Service level authorization',
 'Service Level Authorization is the initial authorization mechanism to ensure clients connecting to a particular Hadoop service have the necessary permissions.',
 'Security', 'Authorization', 'Platform',
 '["Information leakage"]',
 '["Inadequate control of access"]',
 '["Yarn", "Hive", "HDFS"]',
 'By default, service-level authorization is disabled for Hadoop, which means anyone is allowed to submit jobs or see content in HDFS.',
 'LOW', 'CRITICAL',
 '["Set hadoop.security.authorization to true in core-site.xml"]',
 '{"type": "metadata_check", "path": "security.authorization.enabled", "operator": "equals", "expected": true}'),

-- Security > Authorization > Ranger
('SEC-AUTHZ-RANGER', 'Ranger authorization',
 'Ranger enables you to create services for specific Hadoop resources (HDFS, HBase, Hive, etc.) and add access policies to those services.',
 'Security', 'Authorization', 'Ranger',
 '["Information leakage"]',
 '["Inadequate control of access"]',
 '["Yarn", "Hive", "HDFS"]',
 'Users are not restricted in terms of resource or access.',
 'LOW', 'CRITICAL',
 '["Set yarn.acl.enable to true", "Configure RangerYarnAuthorizer, RangerHdfsAuthorizer, RangerHiveAuthorizerFactory"]',
 '{"type": "metadata_check", "path": "security.ranger.enabled", "operator": "equals", "expected": true}'),

-- Security > Access > Firewall
('SEC-ACCESS-FIREWALL', 'Firewall configuration',
 'A firewall is a network security system that monitors and controls incoming and outgoing network traffic.',
 'Security', 'Access', 'Network',
 '["Information leakage", "Theft", "Vandalism", "Industrial espionage", "Access by unauthorized persons"]',
 '["Unprotected network connections"]',
 '["Servers"]',
 'May lead to unauthorized access to or from a private network.',
 'LOW', 'WARNING',
 '["Firewalls should not be present between nodes in the cluster", "Place a firewall around the cluster", "Configure YARN port range: yarn.app.mapreduce.am.job.client.port-range=32000-65000"]',
 '{"type": "metadata_check", "path": "security.firewall.configured", "operator": "equals", "expected": true}'),

-- Security > Access > Edge nodes
('SEC-ACCESS-EDGE', 'Edge/Gateway node isolation',
 'The Gateway nodes are specially client nodes with sufficient firewall port access for only the services planned for access by external processes.',
 'Security', 'Access', 'Knox',
 '["Information leakage"]',
 '["Location vulnerable to flooding", "Uncontrolled copying of data", "Inadequate network management"]',
 '["Servers"]',
 'As a main entrypoint of a cluster, gateway/edge services are the privileged target for attackers.',
 'LOW', 'INFO',
 '["Restrict access such that end users cannot transfer large quantities of data through the gateway", "Use the gateway only for aggregated and reduced sized data subsets"]',
 '{"type": "metadata_check", "path": "security.edge_nodes.configured", "operator": "equals", "expected": true}'),

-- Security > Access > Ambari passwords
('SEC-ACCESS-AMBARI-PWD', 'Ambari database and LDAP password encryption',
 'By default the passwords to access the Ambari database and the LDAP server are stored in a plain text configuration file.',
 'Security', 'Access', 'Ambari',
 '["Information leakage"]',
 '["Inadequate control of remote access"]',
 '["Ambari"]',
 'Plain text passwords in configuration files can be read by anyone with filesystem access.',
 'LOW', 'WARNING',
 '["Use ambari-server setup-security to encrypt passwords stored in ambari.properties", "Provide a master key for encrypting the passwords"]',
 '{"type": "metadata_check", "path": "ambari.passwords_encrypted", "operator": "equals", "expected": true}'),

-- Security > Access > Non-root
('SEC-ACCESS-NONROOT', 'Non-root installation',
 'Applications are meant to be run with non-administrative security so you have to elevate their privileges to interact with the underlying system.',
 'Security', 'Access', 'Ambari',
 '["Vandalism", "Malicious code", "Access by unauthorized persons"]',
 '["Lack of protection for the nodes"]',
 '["Servers"]',
 'Running services as root exposes the system to privilege escalation attacks.',
 'LOW', 'INFO',
 '["Configure Ambari Server and Agent for non-root operation"]',
 '{"type": "metadata_check", "path": "ambari.nonroot_install", "operator": "equals", "expected": true}'),

-- Security > Auditing > Ranger
('SEC-AUDIT-RANGER', 'Ranger auditing',
 'Ranger provides a centralized framework for collecting access audit history and reporting this data.',
 'Security', 'Auditing', 'Ranger',
 '["Information leakage"]',
 '["Unauthorized access"]',
 '["Servers"]',
 'Late observed information leakage without centralized audit trail.',
 'LOW', 'CRITICAL',
 '["Install and configure Ranger for centralized audit logging"]',
 '{"type": "metadata_check", "path": "security.ranger.audit_enabled", "operator": "equals", "expected": true}'),

-- Architecture > Network > Hostnames
('ARCH-NET-HOSTNAMES', 'Hostnames configuration',
 'Make certain both host Fully Qualified Host Names as well as Host aliases are defined and referenceable by all nodes within the cluster.',
 'Architecture', 'Network', 'Network',
 '["Interruption of business processes"]',
 '["Inadequate network management"]',
 '["Servers"]',
 'Service discovery issues if hostnames are not properly configured.',
 'LOW', 'INFO',
 '["Ensure hostname of each system is unique", "Verify all nodes can resolve each other by FQDN"]',
 '{"type": "metadata_check", "path": "network.hostnames_configured", "operator": "equals", "expected": true}'),

-- Architecture > Network > MTU
('ARCH-NET-MTU', 'MTU / Jumbo frames',
 'MTU (Maximum Transmission Unit) for ethernet is 1500. A MTU of 9000 is referred to as a jumbo frame.',
 'Architecture', 'Network', 'Network',
 '["Performance impact"]',
 '["Network overuse"]',
 '["Servers"]',
 'Higher network overheads and CPU cycles, wasted computation resources without jumbo frames.',
 'LOW', 'INFO',
 '["Define MTU for all interfaces to support Jumbo Frames (typically MTU=9000)", "Only if all nodes and switches support this functionality"]',
 '{"type": "metadata_check", "path": "network.mtu_jumbo", "operator": "equals", "expected": true}'),

-- Architecture > Network > Bonding
('ARCH-NET-BONDING', 'NIC Bonding',
 'NIC Bonding provides a method for aggregating multiple network interfaces into a single logical bonded interface.',
 'Architecture', 'Network', 'Network',
 '["Interruption of service", "Failure of communication links"]',
 '["Lack of redundancy"]',
 '["Network"]',
 'Loss of connectivity, interruption of critical service such as the Namenode or any master component.',
 'LOW', 'INFO',
 '["Use multiple interfaces to increase bandwidth and/or availability"]',
 '{"type": "metadata_check", "path": "network.bonding_configured", "operator": "equals", "expected": true}'),

-- Architecture > Network > Multihoming
('ARCH-NET-MULTIHOMING', 'Multihoming configuration',
 'Multihoming is a strategy to assure network performance and predictability for both applications and management purposes.',
 'Architecture', 'Network', 'Network',
 '["Interruption of service"]',
 '["Uncontrolled use of network"]',
 '["Servers"]',
 'Network congestion, direct impact of bandwidth used by clients on admin operations like replication and cluster operations.',
 'LOW', 'WARNING',
 '["Configure dfs.client.use.datanode.hostname=true", "Configure bind-host=0.0.0.0 for namenode, yarn, and mapreduce services"]',
 '{"type": "metadata_check", "path": "network.multihoming_configured", "operator": "equals", "expected": true}'),

-- Architecture > HA > Yarn
('ARCH-HA-YARN', 'Yarn ResourceManager high availability',
 'The ResourceManager is responsible for tracking cluster resources and scheduling applications. Without HA, it is a single point of failure.',
 'Architecture', 'High Availability', 'Yarn',
 '["Interruption of service"]',
 '["Lack of redundancy"]',
 '["Yarn"]',
 'Single point of failure: in case of crash, all Yarn resources provided by NodeManagers will be unavailable.',
 'HIGH', 'CRITICAL',
 '["Configure yarn.resourcemanager.ha.enabled=true", "Configure Active/Standby ResourceManager pair", "Configure ZooKeeper-based automatic failover"]',
 '{"type": "metadata_check", "path": "ha.yarn_rm.enabled", "operator": "equals", "expected": true}'),

-- Architecture > HA > HDFS
('ARCH-HA-HDFS', 'HDFS NameNode high availability',
 'Prior to Hadoop 2.0.0, the NameNode was a single point of failure in an HDFS cluster.',
 'Architecture', 'High Availability', 'HDFS',
 '["Interruption of service"]',
 '["Lack of redundancy"]',
 '["HDFS"]',
 'Single point of failure: in case of crash, all HDFS capacity provided by DataNodes will be unavailable.',
 'HIGH', 'CRITICAL',
 '["Configure HA NameNode with Active/Standby pair", "Configure automatic failover with ZooKeeper", "Configure JournalNodes for shared edit log"]',
 '{"type": "metadata_check", "path": "ha.hdfs_nn.enabled", "operator": "equals", "expected": true}'),

-- Architecture > HA > Knox
('ARCH-HA-KNOX', 'Knox Gateway high availability',
 'Apache Knox Gateway provides a central gateway for Hadoop REST APIs. Without HA, it is a single point of failure.',
 'Architecture', 'High Availability', 'Knox',
 '["Interruption of service"]',
 '["Lack of redundancy"]',
 '["Knox"]',
 'Single point of failure: in case of crash, all clients using Knox will be unavailable.',
 'HIGH', 'CRITICAL',
 '["Install Knox only on Edge servers", "Use multiple instances of Knox Gateway", "Use a load-balancer (HAProxy, mod_proxy) with Sticky Session mode"]',
 '{"type": "metadata_check", "path": "ha.knox.enabled", "operator": "equals", "expected": true}'),

-- Architecture > OS > Transparent Hugepages
('ARCH-OS-THP', 'Transparent Hugepages',
 'Transparent Hugepages is a Linux kernel feature intended to improve performance. It is enabled by default but can cause significant performance problems with Hadoop.',
 'Architecture', 'OS', 'OS',
 '["Significant performance impact compromising platform stability"]',
 '["Inadequate segregation of process using memory"]',
 '["Servers"]',
 'Transparent Hugepages can cause significant performance problems or even apparent memory leaks for Hadoop workloads.',
 'LOW', 'WARNING',
 '["Disable Transparent Hugepages on all nodes in the cluster"]',
 '{"type": "metadata_check", "path": "os.thp_disabled", "operator": "equals", "expected": true}');
