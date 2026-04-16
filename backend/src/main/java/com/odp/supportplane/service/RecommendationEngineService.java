package com.odp.supportplane.service;

import com.odp.supportplane.config.AccessControl;
import com.odp.supportplane.dto.response.AuditReportResponse;
import com.odp.supportplane.dto.response.ComponentSummaryResponse;
import com.odp.supportplane.model.AuditRun;
import com.odp.supportplane.model.Cluster;
import com.odp.supportplane.model.Recommendation;
import com.odp.supportplane.model.RecommendationRule;
import com.odp.supportplane.model.User;
import com.odp.supportplane.repository.AuditRunRepository;
import com.odp.supportplane.repository.ClusterRepository;
import com.odp.supportplane.repository.RecommendationRepository;
import com.odp.supportplane.repository.RecommendationRuleRepository;
import com.odp.supportplane.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationEngineService {

    private final RecommendationRuleRepository ruleRepository;
    private final RecommendationRepository recommendationRepository;
    private final AuditRunRepository auditRunRepository;
    private final ClusterRepository clusterRepository;
    private final UserRepository userRepository;

    @Transactional
    public AuditRun evaluateCluster(Long clusterId, Long triggeredById) {
        AccessControl.requireOperator();

        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));

        User triggeredBy = triggeredById != null
                ? userRepository.findById(triggeredById).orElse(null)
                : null;

        AuditRun run = AuditRun.builder()
                .cluster(cluster)
                .triggeredBy(triggeredBy)
                .build();
        run = auditRunRepository.save(run);

        try {
            List<RecommendationRule> rules = ruleRepository.findByEnabledTrueOrderByCategoryAscComponentAsc();

            // Delete previous engine-generated recommendations for this cluster
            recommendationRepository.deleteByClusterIdAndSource(clusterId, "ENGINE");

            Map<String, Object> metadata = cluster.getMetadata() != null
                    ? cluster.getMetadata()
                    : Collections.emptyMap();

            int findingsCount = 0;
            Map<String, Integer> summaryCounts = new HashMap<>();
            summaryCounts.put("OK", 0);
            summaryCounts.put("WARNING", 0);
            summaryCounts.put("CRITICAL", 0);
            summaryCounts.put("UNKNOWN", 0);

            for (RecommendationRule rule : rules) {
                String findingStatus = evaluateRule(rule, metadata);
                String severity = mapFindingStatusToSeverity(findingStatus, rule.getDefaultSeverity());
                String risk = calculateRisk(rule.getDefaultLikelihood(), severity);

                Recommendation rec = Recommendation.builder()
                        .cluster(cluster)
                        .title(rule.getTitle())
                        .description(rule.getDescription())
                        .severity(severity)
                        .source("ENGINE")
                        .status("DRAFT")
                        .rule(rule)
                        .category(rule.getCategory())
                        .subcategory(rule.getSubcategory())
                        .component(rule.getComponent())
                        .threat(rule.getThreat())
                        .vulnerability(rule.getVulnerability())
                        .asset(rule.getAsset())
                        .impact(rule.getImpact())
                        .likelihood(rule.getDefaultLikelihood())
                        .risk(risk)
                        .recommendationsText(rule.getRecommendationsText())
                        .findingStatus(findingStatus)
                        .build();
                recommendationRepository.save(rec);

                findingsCount++;
                summaryCounts.merge(findingStatus, 1, Integer::sum);
            }

            run.setRulesEvaluated(rules.size());
            run.setFindingsCount(findingsCount);
            run.setSummary(new HashMap<>(summaryCounts));
            run.setStatus("COMPLETED");
            run.setCompletedAt(LocalDateTime.now());
            return auditRunRepository.save(run);

        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setCompletedAt(LocalDateTime.now());
            Map<String, Object> errorSummary = new HashMap<>();
            errorSummary.put("error", e.getMessage());
            run.setSummary(errorSummary);
            auditRunRepository.save(run);
            throw e;
        }
    }

    public String evaluateRule(RecommendationRule rule, Map<String, Object> metadata) {
        Map<String, Object> condition = rule.getCondition();
        if (condition == null || condition.isEmpty()) {
            return "UNKNOWN";
        }

        String type = (String) condition.get("type");
        if (type == null) {
            return "UNKNOWN";
        }

        switch (type) {
            case "metadata_check":
                return evaluateMetadataCheck(condition, metadata, rule.getDefaultSeverity());
            case "metadata_absent":
                return evaluateMetadataAbsent(condition, metadata, rule.getDefaultSeverity());
            case "threshold_check":
                return evaluateThresholdCheck(condition, metadata, rule.getDefaultSeverity());
            case "range_check":
                return evaluateRangeCheck(condition, metadata, rule.getDefaultSeverity());
            case "list_not_empty":
                return evaluateListNotEmpty(condition, metadata, rule.getDefaultSeverity());
            case "always":
                return "UNKNOWN";
            default:
                return "UNKNOWN";
        }
    }

    private String evaluateMetadataCheck(Map<String, Object> condition,
                                          Map<String, Object> metadata,
                                          String defaultSeverity) {
        String path = (String) condition.get("path");
        String operator = (String) condition.get("operator");
        Object expected = condition.get("expected");

        if (path == null || operator == null) {
            return "UNKNOWN";
        }

        Object actual = navigatePath(metadata, path);

        // If the value is absent, the check fails (unless absent_triggers is explicitly false)
        if (actual == null) {
            Boolean absentTriggers = (Boolean) condition.getOrDefault("absent_triggers", true);
            if (Boolean.TRUE.equals(absentTriggers)) {
                return severityToFindingStatus(defaultSeverity);
            }
            return "UNKNOWN";
        }

        boolean matches = evaluateOperator(operator, actual, expected);
        return matches ? "OK" : severityToFindingStatus(defaultSeverity);
    }

    private String evaluateMetadataAbsent(Map<String, Object> condition,
                                           Map<String, Object> metadata,
                                           String defaultSeverity) {
        String path = (String) condition.get("path");
        if (path == null) {
            return "UNKNOWN";
        }

        Object actual = navigatePath(metadata, path);
        return actual == null
                ? severityToFindingStatus(defaultSeverity)
                : "OK";
    }

    @SuppressWarnings("unchecked")
    private Object navigatePath(Map<String, Object> metadata, String path) {
        String[] parts = path.split("\\.");
        Object current = metadata;
        for (int i = 0; i < parts.length; i++) {
            if (current instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) current;
                // Try exact key first
                if (map.containsKey(parts[i])) {
                    current = map.get(parts[i]);
                } else {
                    // Try joining remaining parts as a composite key (e.g. "vm.swappiness")
                    String compositeKey = String.join(".", java.util.Arrays.copyOfRange(parts, i, parts.length));
                    if (map.containsKey(compositeKey)) {
                        return map.get(compositeKey);
                    }
                    return null;
                }
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private boolean evaluateOperator(String operator, Object actual, Object expected) {
        switch (operator) {
            case "equals":
                if (actual instanceof Number && expected instanceof Number) {
                    return ((Number) actual).doubleValue() == ((Number) expected).doubleValue();
                }
                return Objects.equals(actual, expected);

            case "not_equals":
                return !Objects.equals(actual, expected);

            case "greater_than":
                if (actual instanceof Number && expected instanceof Number) {
                    return ((Number) actual).doubleValue() > ((Number) expected).doubleValue();
                }
                return false;

            case "less_than":
                if (actual instanceof Number && expected instanceof Number) {
                    return ((Number) actual).doubleValue() < ((Number) expected).doubleValue();
                }
                return false;

            case "contains":
                if (actual instanceof String && expected instanceof String) {
                    return ((String) actual).contains((String) expected);
                }
                return false;

            default:
                return false;
        }
    }

    /**
     * Threshold check: triggers when a numeric value exceeds a threshold.
     * Condition: {"type": "threshold_check", "path": "...", "threshold": N, "direction": "above"|"below"}
     * Useful for benchmarks (e.g., disk latency > 10ms, CPU steal > 5%).
     */
    private String evaluateThresholdCheck(Map<String, Object> condition,
                                           Map<String, Object> metadata,
                                           String defaultSeverity) {
        String path = (String) condition.get("path");
        Object thresholdObj = condition.get("threshold");
        String direction = (String) condition.getOrDefault("direction", "above");

        if (path == null || thresholdObj == null) return "UNKNOWN";

        Object actual = navigatePath(metadata, path);
        if (actual == null) {
            Boolean absentTriggers = (Boolean) condition.getOrDefault("absent_triggers", true);
            return Boolean.TRUE.equals(absentTriggers) ? severityToFindingStatus(defaultSeverity) : "UNKNOWN";
        }

        if (actual instanceof Number && thresholdObj instanceof Number) {
            double actualVal = ((Number) actual).doubleValue();
            double threshold = ((Number) thresholdObj).doubleValue();
            boolean triggered = "below".equals(direction)
                    ? actualVal < threshold
                    : actualVal > threshold;
            return triggered ? severityToFindingStatus(defaultSeverity) : "OK";
        }
        return "UNKNOWN";
    }

    /**
     * Range check: value must be within [min, max] range.
     * Condition: {"type": "range_check", "path": "...", "min": N, "max": N}
     * Useful for kernel params (e.g., swappiness should be 1-10 for Hadoop).
     */
    private String evaluateRangeCheck(Map<String, Object> condition,
                                       Map<String, Object> metadata,
                                       String defaultSeverity) {
        String path = (String) condition.get("path");
        Object minObj = condition.get("min");
        Object maxObj = condition.get("max");

        if (path == null) return "UNKNOWN";

        Object actual = navigatePath(metadata, path);
        if (actual == null) return "UNKNOWN";

        if (actual instanceof Number) {
            double val = ((Number) actual).doubleValue();
            if (minObj instanceof Number && val < ((Number) minObj).doubleValue()) {
                return severityToFindingStatus(defaultSeverity);
            }
            if (maxObj instanceof Number && val > ((Number) maxObj).doubleValue()) {
                return severityToFindingStatus(defaultSeverity);
            }
            return "OK";
        }
        // Try parsing string as number
        if (actual instanceof String) {
            try {
                double val = Double.parseDouble((String) actual);
                if (minObj instanceof Number && val < ((Number) minObj).doubleValue()) {
                    return severityToFindingStatus(defaultSeverity);
                }
                if (maxObj instanceof Number && val > ((Number) maxObj).doubleValue()) {
                    return severityToFindingStatus(defaultSeverity);
                }
                return "OK";
            } catch (NumberFormatException e) {
                return "UNKNOWN";
            }
        }
        return "UNKNOWN";
    }

    /**
     * List not empty: triggers if a list/array at path is non-empty (e.g., active critical alerts).
     * Condition: {"type": "list_not_empty", "path": "..."}
     */
    @SuppressWarnings("unchecked")
    private String evaluateListNotEmpty(Map<String, Object> condition,
                                         Map<String, Object> metadata,
                                         String defaultSeverity) {
        String path = (String) condition.get("path");
        if (path == null) return "UNKNOWN";

        Object actual = navigatePath(metadata, path);
        if (actual == null) return "OK";

        if (actual instanceof java.util.Collection) {
            return ((java.util.Collection<?>) actual).isEmpty() ? "OK" : severityToFindingStatus(defaultSeverity);
        }
        return "UNKNOWN";
    }

    private String severityToFindingStatus(String severity) {
        if (severity == null) return "WARNING";
        switch (severity) {
            case "CRITICAL": return "CRITICAL";
            case "WARNING": return "WARNING";
            case "INFO": return "OK";
            default: return "WARNING";
        }
    }

    private String mapFindingStatusToSeverity(String findingStatus, String defaultSeverity) {
        switch (findingStatus) {
            case "OK": return "INFO";
            case "WARNING": return "WARNING";
            case "CRITICAL": return "CRITICAL";
            case "UNKNOWN": return defaultSeverity != null ? defaultSeverity : "WARNING";
            default: return defaultSeverity != null ? defaultSeverity : "WARNING";
        }
    }

    public String calculateRisk(String likelihood, String severity) {
        int likelihoodScore = levelToScore(likelihood);
        int severityScore = levelToScore(severity);
        int riskScore = likelihoodScore * severityScore;

        if (riskScore >= 6) return "HIGH";
        if (riskScore >= 3) return "MEDIUM";
        return "LOW";
    }

    private int levelToScore(String level) {
        if (level == null) return 2;
        switch (level.toUpperCase()) {
            case "HIGH":
            case "CRITICAL":
                return 3;
            case "MEDIUM":
            case "WARNING":
                return 2;
            case "LOW":
            case "INFO":
                return 1;
            default:
                return 2;
        }
    }

    // --- Report & Summary ---

    public AuditReportResponse getAuditReport(Long clusterId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found"));

        List<Recommendation> findings = recommendationRepository
                .findByClusterIdAndSourceOrderByCategoryAscComponentAsc(clusterId, "ENGINE");

        AuditReportResponse report = new AuditReportResponse();
        report.setClusterName(cluster.getName() != null ? cluster.getName() : cluster.getClusterId());
        report.setClusterId(cluster.getClusterId());
        report.setGeneratedAt(LocalDateTime.now());

        // Summary counts
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("total", findings.size());
        summary.put("OK", 0);
        summary.put("WARNING", 0);
        summary.put("CRITICAL", 0);
        summary.put("UNKNOWN", 0);
        for (Recommendation f : findings) {
            String fs = f.getFindingStatus() != null ? f.getFindingStatus() : "UNKNOWN";
            summary.merge(fs, 1, Integer::sum);
        }
        report.setSummary(summary);

        // Group by category, then subcategory
        Map<String, List<Recommendation>> byCategory = findings.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCategory() != null ? r.getCategory() : "Other",
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<AuditReportResponse.CategoryGroup> categoryGroups = new ArrayList<>();
        for (Map.Entry<String, List<Recommendation>> catEntry : byCategory.entrySet()) {
            AuditReportResponse.CategoryGroup catGroup = new AuditReportResponse.CategoryGroup();
            catGroup.setName(catEntry.getKey());

            Map<String, List<Recommendation>> bySubcategory = catEntry.getValue().stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getSubcategory() != null ? r.getSubcategory() : "",
                            LinkedHashMap::new,
                            Collectors.toList()));

            // Findings without subcategory go directly on the category
            List<Recommendation> noSubcat = bySubcategory.getOrDefault("", Collections.emptyList());
            catGroup.setFindings(noSubcat.stream().map(this::toFindingResponse).collect(Collectors.toList()));

            // Subcategories
            List<AuditReportResponse.SubcategoryGroup> subcatGroups = new ArrayList<>();
            for (Map.Entry<String, List<Recommendation>> subEntry : bySubcategory.entrySet()) {
                if (subEntry.getKey().isEmpty()) continue;
                AuditReportResponse.SubcategoryGroup subGroup = new AuditReportResponse.SubcategoryGroup();
                subGroup.setName(subEntry.getKey());
                subGroup.setFindings(subEntry.getValue().stream()
                        .map(this::toFindingResponse)
                        .collect(Collectors.toList()));
                subcatGroups.add(subGroup);
            }
            catGroup.setSubcategories(subcatGroups);
            categoryGroups.add(catGroup);
        }
        report.setCategories(categoryGroups);
        return report;
    }

    private AuditReportResponse.FindingResponse toFindingResponse(Recommendation rec) {
        AuditReportResponse.FindingResponse f = new AuditReportResponse.FindingResponse();
        f.setId(rec.getId());
        if (rec.getRule() != null) {
            f.setRuleCode(rec.getRule().getCode());
        }
        f.setTitle(rec.getTitle());
        f.setDescription(rec.getDescription());
        f.setFindingStatus(rec.getFindingStatus());
        f.setComponent(rec.getComponent());
        f.setThreat(rec.getThreat());
        f.setVulnerability(rec.getVulnerability());
        f.setAsset(rec.getAsset());
        f.setImpact(rec.getImpact());
        f.setLikelihood(rec.getLikelihood());
        f.setRisk(rec.getRisk());
        f.setSeverity(rec.getSeverity());
        f.setRecommendationsText(rec.getRecommendationsText());
        f.setStatus(rec.getStatus());
        return f;
    }

    public List<ComponentSummaryResponse> getComponentSummary(Long clusterId) {
        List<Recommendation> findings = recommendationRepository
                .findByClusterIdAndSourceOrderByCategoryAscComponentAsc(clusterId, "ENGINE");

        Map<String, ComponentSummaryResponse> componentMap = new LinkedHashMap<>();

        for (Recommendation rec : findings) {
            String comp = rec.getComponent() != null ? rec.getComponent() : "Other";
            ComponentSummaryResponse summary = componentMap.computeIfAbsent(comp,
                    k -> new ComponentSummaryResponse(k, 0, 0, 0, 0));

            String fs = rec.getFindingStatus() != null ? rec.getFindingStatus() : "UNKNOWN";
            switch (fs) {
                case "OK":
                    summary.setOkCount(summary.getOkCount() + 1);
                    break;
                case "WARNING":
                    summary.setWarningCount(summary.getWarningCount() + 1);
                    break;
                case "CRITICAL":
                    summary.setCriticalCount(summary.getCriticalCount() + 1);
                    break;
                default:
                    summary.setUnknownCount(summary.getUnknownCount() + 1);
                    break;
            }
        }

        return new ArrayList<>(componentMap.values());
    }

    public List<AuditRun> getAuditRuns(Long clusterId) {
        return auditRunRepository.findByClusterIdOrderByStartedAtDesc(clusterId);
    }
}
