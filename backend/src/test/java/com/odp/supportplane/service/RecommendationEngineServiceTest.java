package com.odp.supportplane.service;

import com.odp.supportplane.model.RecommendationRule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationEngineServiceTest {

    private final RecommendationEngineService engine = new RecommendationEngineService(
            null, null, null, null, null);

    // --- evaluateRule tests ---

    @Test
    void evaluateRule_metadataCheck_matchesTrue() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "metadata_check",
                        "path", "security.kerberos.enabled",
                        "operator", "equals",
                        "expected", true
                ))
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("security", Map.of("kerberos", Map.of("enabled", true)));

        assertEquals("OK", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_metadataCheck_matchesFalse() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "metadata_check",
                        "path", "security.kerberos.enabled",
                        "operator", "equals",
                        "expected", true
                ))
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("security", Map.of("kerberos", Map.of("enabled", false)));

        assertEquals("CRITICAL", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_metadataCheck_missingPath_triggersDefault() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of(
                        "type", "metadata_check",
                        "path", "security.kerberos.enabled",
                        "operator", "equals",
                        "expected", true
                ))
                .build();

        Map<String, Object> metadata = new HashMap<>();

        assertEquals("WARNING", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_metadataAbsent_pathMissing() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "metadata_absent",
                        "path", "security.ranger"
                ))
                .build();

        Map<String, Object> metadata = new HashMap<>();

        assertEquals("CRITICAL", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_metadataAbsent_pathPresent() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "metadata_absent",
                        "path", "security.ranger"
                ))
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("security", Map.of("ranger", Map.of("enabled", true)));

        assertEquals("OK", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_always_returnsUnknown() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of("type", "always"))
                .build();

        assertEquals("UNKNOWN", engine.evaluateRule(rule, new HashMap<>()));
    }

    @Test
    void evaluateRule_noCondition_returnsUnknown() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .build();

        assertEquals("UNKNOWN", engine.evaluateRule(rule, new HashMap<>()));
    }

    @Test
    void evaluateRule_greaterThan_passes() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of(
                        "type", "metadata_check",
                        "path", "yarn.queues_count",
                        "operator", "greater_than",
                        "expected", 1
                ))
                .build();

        Map<String, Object> metadata = Map.of("yarn", Map.of("queues_count", 3));

        assertEquals("OK", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_greaterThan_fails() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of(
                        "type", "metadata_check",
                        "path", "yarn.queues_count",
                        "operator", "greater_than",
                        "expected", 1
                ))
                .build();

        Map<String, Object> metadata = Map.of("yarn", Map.of("queues_count", 1));

        assertEquals("WARNING", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_lessThan_passes() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("INFO")
                .condition(Map.of(
                        "type", "metadata_check",
                        "path", "hdfs.small_files_ratio",
                        "operator", "less_than",
                        "expected", 0.3
                ))
                .build();

        Map<String, Object> metadata = Map.of("hdfs", Map.of("small_files_ratio", 0.1));

        assertEquals("OK", engine.evaluateRule(rule, metadata));
    }

    // --- threshold_check tests ---

    @Test
    void evaluateRule_thresholdAbove_triggers() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "threshold_check",
                        "path", "benchmarks.cpu.steal_time.percent",
                        "threshold", 5,
                        "direction", "above"
                ))
                .build();

        Map<String, Object> metadata = Map.of("benchmarks",
                Map.of("cpu", Map.of("steal_time", Map.of("percent", 8.5))));

        assertEquals("CRITICAL", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_thresholdAbove_passes() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "threshold_check",
                        "path", "benchmarks.cpu.steal_time.percent",
                        "threshold", 5,
                        "direction", "above"
                ))
                .build();

        Map<String, Object> metadata = Map.of("benchmarks",
                Map.of("cpu", Map.of("steal_time", Map.of("percent", 2.0))));

        assertEquals("OK", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_thresholdBelow_triggers() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of(
                        "type", "threshold_check",
                        "path", "benchmarks.disk.sequential_write.throughput_mb_per_second",
                        "threshold", 100,
                        "direction", "below"
                ))
                .build();

        Map<String, Object> metadata = Map.of("benchmarks",
                Map.of("disk", Map.of("sequential_write", Map.of("throughput_mb_per_second", 45.0))));

        assertEquals("WARNING", engine.evaluateRule(rule, metadata));
    }

    // --- range_check tests ---

    @Test
    void evaluateRule_rangeCheck_inRange() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of(
                        "type", "range_check",
                        "path", "benchmarks.memory.swap_analysis.swappiness",
                        "min", 0,
                        "max", 10
                ))
                .build();

        Map<String, Object> metadata = Map.of("benchmarks",
                Map.of("memory", Map.of("swap_analysis", Map.of("swappiness", 1))));

        assertEquals("OK", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_rangeCheck_tooHigh() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of(
                        "type", "range_check",
                        "path", "benchmarks.memory.swap_analysis.swappiness",
                        "min", 0,
                        "max", 10
                ))
                .build();

        Map<String, Object> metadata = Map.of("benchmarks",
                Map.of("memory", Map.of("swap_analysis", Map.of("swappiness", 60))));

        assertEquals("WARNING", engine.evaluateRule(rule, metadata));
    }

    // --- list_not_empty tests ---

    @Test
    void evaluateRule_listNotEmpty_emptyList() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "list_not_empty",
                        "path", "service_health.active_alerts"
                ))
                .build();

        Map<String, Object> metadata = Map.of("service_health",
                Map.of("active_alerts", java.util.List.of()));

        assertEquals("OK", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_listNotEmpty_hasItems() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "list_not_empty",
                        "path", "service_health.active_alerts"
                ))
                .build();

        Map<String, Object> metadata = Map.of("service_health",
                Map.of("active_alerts", java.util.List.of(Map.of("label", "test"))));

        assertEquals("CRITICAL", engine.evaluateRule(rule, metadata));
    }

    // --- count_check tests ---

    @Test
    void evaluateRule_countCheck_listBelow() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of(
                        "type", "count_check",
                        "path", "topology.hosts",
                        "threshold", 3,
                        "direction", "below"
                ))
                .build();

        Map<String, Object> metadata = Map.of("topology",
                Map.of("hosts", java.util.List.of(Map.of("h", "a"), Map.of("h", "b"))));

        assertEquals("WARNING", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_countCheck_listAbove() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of(
                        "type", "count_check",
                        "path", "topology.hosts",
                        "threshold", 1,
                        "direction", "below"
                ))
                .build();

        Map<String, Object> metadata = Map.of("topology",
                Map.of("hosts", java.util.List.of(Map.of("h", "a"), Map.of("h", "b"), Map.of("h", "c"))));

        assertEquals("OK", engine.evaluateRule(rule, metadata));
    }

    // --- multi_path_check tests ---

    @Test
    void evaluateRule_multiPathCheck_allPass() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "multi_path_check",
                        "checks", java.util.List.of(
                                Map.of("path", "security.kerberos.enabled", "operator", "equals", "expected", true),
                                Map.of("path", "security.ranger.enabled", "operator", "equals", "expected", true)
                        )
                ))
                .build();

        Map<String, Object> metadata = Map.of("security",
                Map.of("kerberos", Map.of("enabled", true), "ranger", Map.of("enabled", true)));

        assertEquals("OK", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_multiPathCheck_oneFails() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("CRITICAL")
                .condition(Map.of(
                        "type", "multi_path_check",
                        "checks", java.util.List.of(
                                Map.of("path", "security.kerberos.enabled", "operator", "equals", "expected", true),
                                Map.of("path", "security.ranger.enabled", "operator", "equals", "expected", true)
                        )
                ))
                .build();

        Map<String, Object> metadata = Map.of("security",
                Map.of("kerberos", Map.of("enabled", true), "ranger", Map.of("enabled", false)));

        assertEquals("CRITICAL", engine.evaluateRule(rule, metadata));
    }

    @Test
    void evaluateRule_multiPathCheck_pathMissing() {
        RecommendationRule rule = RecommendationRule.builder()
                .defaultSeverity("WARNING")
                .condition(Map.of(
                        "type", "multi_path_check",
                        "checks", java.util.List.of(
                                Map.of("path", "security.kerberos.enabled", "operator", "equals", "expected", true)
                        )
                ))
                .build();

        Map<String, Object> metadata = Map.of("security", Map.of());

        assertEquals("WARNING", engine.evaluateRule(rule, metadata));
    }

    // --- calculateRisk tests ---

    @Test
    void calculateRisk_highHigh_isHigh() {
        assertEquals("HIGH", engine.calculateRisk("HIGH", "CRITICAL"));
    }

    @Test
    void calculateRisk_lowLow_isLow() {
        assertEquals("LOW", engine.calculateRisk("LOW", "INFO"));
    }

    @Test
    void calculateRisk_mediumMedium_isMedium() {
        assertEquals("MEDIUM", engine.calculateRisk("MEDIUM", "WARNING"));
    }

    @Test
    void calculateRisk_highLow_isMedium() {
        assertEquals("MEDIUM", engine.calculateRisk("HIGH", "INFO"));
    }

    @Test
    void calculateRisk_lowHigh_isMedium() {
        assertEquals("MEDIUM", engine.calculateRisk("LOW", "CRITICAL"));
    }
}
