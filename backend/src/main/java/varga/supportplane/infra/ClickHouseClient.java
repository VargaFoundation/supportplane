package varga.supportplane.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * ClickHouse client for time-series metric storage.
 *
 * Provides batch insert and analytical query capabilities
 * for cluster metrics, JMX data, and benchmarks.
 */
@Component
@Slf4j
public class ClickHouseClient {

    @Value("${clickhouse.url:jdbc:ch://localhost:8123/supportplane}")
    private String url;

    @Value("${clickhouse.username:default}")
    private String username;

    @Value("${clickhouse.password:}")
    private String password;

    @Value("${clickhouse.enabled:false}")
    private boolean enabled;

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("ClickHouse integration disabled (clickhouse.enabled=false)");
            return;
        }
        try (Connection conn = getConnection()) {
            log.info("ClickHouse connection established: {}", url);
        } catch (SQLException e) {
            log.warn("ClickHouse not available at {}: {}. Metric ingestion will be skipped.", url, e.getMessage());
        }
    }

    /**
     * Batch insert host-level metrics.
     */
    public void insertMetrics(long clusterId, String nodeId,
                                Map<String, Double> metrics, Instant timestamp) {
        if (!enabled || metrics.isEmpty()) return;

        String sql = "INSERT INTO metric_points (cluster_id, node_id, metric_name, value, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                ps.setLong(1, clusterId);
                ps.setString(2, nodeId);
                ps.setString(3, entry.getKey());
                ps.setDouble(4, entry.getValue());
                ps.setTimestamp(5, Timestamp.from(timestamp));
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("Inserted {} metric points for cluster={}, node={}", metrics.size(), clusterId, nodeId);
        } catch (SQLException e) {
            log.warn("Failed to insert metrics into ClickHouse: {}", e.getMessage());
        }
    }

    /**
     * Batch insert JMX service metrics.
     */
    public void insertJmxMetrics(long clusterId, String nodeId, String service,
                                   String component, Map<String, Double> metrics,
                                   Instant timestamp) {
        if (!enabled || metrics.isEmpty()) return;

        String sql = "INSERT INTO jmx_points (cluster_id, node_id, service, component, metric_name, value, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                ps.setLong(1, clusterId);
                ps.setString(2, nodeId);
                ps.setString(3, service);
                ps.setString(4, component);
                ps.setString(5, entry.getKey());
                ps.setDouble(6, entry.getValue());
                ps.setTimestamp(7, Timestamp.from(timestamp));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            log.warn("Failed to insert JMX metrics: {}", e.getMessage());
        }
    }

    /**
     * Query metric statistics for anomaly detection.
     * Returns: metric_name -> {mean, stddev, min, max, p95, current, sample_count}
     */
    public Map<String, Map<String, Double>> queryMetricStats(long clusterId, int daysBack) {
        if (!enabled) return Collections.emptyMap();

        String sql = """
            SELECT metric_name,
                   avg(value) AS mean,
                   stddevPop(value) AS stddev,
                   min(value) AS min_val,
                   max(value) AS max_val,
                   quantile(0.95)(value) AS p95,
                   argMax(value, timestamp) AS latest,
                   count() AS cnt
            FROM metric_points
            WHERE cluster_id = ? AND timestamp > now() - INTERVAL ? DAY
            GROUP BY metric_name
            HAVING cnt >= 5
            """;

        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clusterId);
            ps.setInt(2, daysBack);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Double> stats = new LinkedHashMap<>();
                    stats.put("mean", rs.getDouble("mean"));
                    stats.put("stddev", rs.getDouble("stddev"));
                    stats.put("min", rs.getDouble("min_val"));
                    stats.put("max", rs.getDouble("max_val"));
                    stats.put("p95", rs.getDouble("p95"));
                    stats.put("latest", rs.getDouble("latest"));
                    stats.put("sample_count", rs.getDouble("cnt"));
                    result.put(rs.getString("metric_name"), stats);
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to query metric stats from ClickHouse: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Query time series for a specific metric (for trend analysis / predictions).
     * Returns list of [timestamp_epoch_ms, value] pairs.
     */
    public List<double[]> queryTimeSeries(long clusterId, String metricName, int daysBack) {
        if (!enabled) return Collections.emptyList();

        String sql = """
            SELECT toUnixTimestamp64Milli(timestamp) AS ts, avg(value) AS val
            FROM metric_points
            WHERE cluster_id = ? AND metric_name = ? AND timestamp > now() - INTERVAL ? DAY
            GROUP BY toStartOfHour(timestamp) AS ts_hour
            ORDER BY ts_hour
            """;

        List<double[]> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clusterId);
            ps.setString(2, metricName);
            ps.setInt(3, daysBack);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new double[]{rs.getDouble("ts"), rs.getDouble("val")});
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to query time series: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Query available metric names for a cluster.
     */
    public List<String> queryMetricNames(long clusterId) {
        if (!enabled) return Collections.emptyList();

        String sql = "SELECT DISTINCT metric_name FROM metric_points WHERE cluster_id = ? ORDER BY metric_name";
        List<String> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, clusterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("metric_name"));
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to query metric names: {}", e.getMessage());
        }
        return result;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
