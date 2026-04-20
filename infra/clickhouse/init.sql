-- ClickHouse schema for SupportPlane time-series metrics

CREATE DATABASE IF NOT EXISTS supportplane;

-- Host-level metrics (CPU, memory, disk, network per node)
CREATE TABLE IF NOT EXISTS supportplane.metric_points (
    cluster_id    UInt64,
    node_id       String,
    metric_name   LowCardinality(String),
    value         Float64,
    timestamp     DateTime64(3)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (cluster_id, metric_name, node_id, timestamp)
TTL toDateTime(timestamp) + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

-- Service JMX metrics (HDFS NameNode heap, YARN apps, HBase RS, etc.)
CREATE TABLE IF NOT EXISTS supportplane.jmx_points (
    cluster_id    UInt64,
    node_id       String,
    service       LowCardinality(String),
    component     LowCardinality(String),
    metric_name   LowCardinality(String),
    value         Float64,
    timestamp     DateTime64(3)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (cluster_id, service, metric_name, timestamp)
TTL toDateTime(timestamp) + INTERVAL 90 DAY;

-- Benchmark results (CPU, disk, memory, network tests)
CREATE TABLE IF NOT EXISTS supportplane.benchmark_points (
    cluster_id    UInt64,
    node_id       String,
    test_name     LowCardinality(String),
    metric_name   LowCardinality(String),
    value         Float64,
    timestamp     DateTime64(3)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (cluster_id, test_name, metric_name, timestamp)
TTL toDateTime(timestamp) + INTERVAL 90 DAY;

-- Materialized view: hourly aggregates for fast AI queries
CREATE MATERIALIZED VIEW IF NOT EXISTS supportplane.metric_hourly_mv
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (cluster_id, metric_name, node_id, hour)
AS SELECT
    cluster_id,
    node_id,
    metric_name,
    toStartOfHour(timestamp) AS hour,
    avg(value) AS avg_val,
    min(value) AS min_val,
    max(value) AS max_val,
    stddevPop(value) AS stddev_val,
    quantile(0.95)(value) AS p95_val,
    count() AS sample_count
FROM supportplane.metric_points
GROUP BY cluster_id, node_id, metric_name, hour;

-- Materialized view: daily aggregates for trend analysis
CREATE MATERIALIZED VIEW IF NOT EXISTS supportplane.metric_daily_mv
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(day)
ORDER BY (cluster_id, metric_name, day)
AS SELECT
    cluster_id,
    metric_name,
    toDate(timestamp) AS day,
    avg(value) AS avg_val,
    min(value) AS min_val,
    max(value) AS max_val,
    stddevPop(value) AS stddev_val,
    count() AS sample_count
FROM supportplane.metric_points
GROUP BY cluster_id, metric_name, day;
