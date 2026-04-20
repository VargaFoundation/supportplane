package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.LogCluster;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Log template extraction using a Drain-like algorithm.
 *
 * Based on: He et al. (2017) "Drain: An Online Log Parsing Approach with Fixed Depth Tree"
 *
 * Key idea: parse log messages into a fixed-depth tree where:
 * - Level 1: log message length (number of tokens)
 * - Level 2: first token (usually service/class name)
 * - Leaf: template with wildcards (<*>) replacing variable parts
 *
 * Adapted for Hadoop log formats with stack trace handling and
 * multi-line message support.
 */
public class LogTokenizer {

    // Patterns for variable tokens (replaced by <*>)
    private static final List<Pattern> VARIABLE_PATTERNS = List.of(
            Pattern.compile("\\d{4}[-/]\\d{2}[-/]\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}[.,]?\\d*"), // timestamps
            Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?"),           // IP addresses
            Pattern.compile("/[\\w/.-]+"),                                                    // file paths
            Pattern.compile("\\b[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\b"),            // UUIDs
            Pattern.compile("\\bblk_-?\\d+\\b"),                                             // HDFS block IDs
            Pattern.compile("\\b\\d{5,}\\b"),                                                // long numbers (PIDs, ports, sizes)
            Pattern.compile("0x[0-9a-fA-F]+"),                                               // hex values
            Pattern.compile("\\b\\d+\\.\\d+\\.\\d+\\b"),                                     // version numbers
            Pattern.compile("attempt_\\d+_\\d+_[mr]_\\d+_\\d+"),                            // YARN attempt IDs
            Pattern.compile("application_\\d+_\\d+"),                                        // YARN app IDs
            Pattern.compile("container_\\d+_\\d+_\\d+_\\d+")                                // YARN container IDs
    );

    private static final int MAX_TEMPLATES_PER_KEY = 50;
    private static final int MAX_TREE_KEYS = 5000;

    private final double similarityThreshold;
    private final Map<String, List<TemplateNode>> templateTree = new LinkedHashMap<>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<TemplateNode>> eldest) {
            return size() > MAX_TREE_KEYS;
        }
    };

    public LogTokenizer(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public LogTokenizer() {
        this(0.5);
    }

    /**
     * Parse log lines into clusters with extracted templates.
     *
     * @param logLines list of raw log lines
     * @return list of log clusters with templates and counts
     */
    public List<LogCluster> parse(List<String> logLines) {
        Map<Integer, TemplateNode> clusterMap = new LinkedHashMap<>();
        int nextId = 0;

        for (String line : logLines) {
            if (line == null || line.isBlank()) continue;

            // Pre-process: extract severity and normalize
            String severity = extractSeverity(line);
            String service = extractService(line);
            String[] tokens = tokenize(line);

            if (tokens.length == 0) continue;

            // Replace variable tokens with <*>
            String[] normalized = normalizeTokens(tokens);
            String key = normalized.length + ":" + normalized[0];

            // Find matching template or create new one
            List<TemplateNode> candidates = templateTree.computeIfAbsent(key, k -> new ArrayList<>());
            TemplateNode matched = findMatchingTemplate(normalized, candidates);

            if (matched != null) {
                matched.count++;
                matched.updateTemplate(normalized);
                if (!matched.samples.contains(line) && matched.samples.size() < 3) {
                    matched.samples.add(line.length() > 200 ? line.substring(0, 200) : line);
                }
            } else if (candidates.size() < MAX_TEMPLATES_PER_KEY) {
                TemplateNode newNode = new TemplateNode(nextId++, normalized, severity, service);
                newNode.samples.add(line.length() > 200 ? line.substring(0, 200) : line);
                candidates.add(newNode);
                clusterMap.put(newNode.id, newNode);
            }
        }

        // Convert to LogCluster results
        List<LogCluster> results = new ArrayList<>();
        for (TemplateNode node : clusterMap.values()) {
            if (node == null) continue;
            // Recalculate from template tree (some were updated)
            results.add(LogCluster.builder()
                    .clusterId(node.id)
                    .template(String.join(" ", node.template))
                    .occurrenceCount(node.count)
                    .severityScore(computeSeverityScore(node))
                    .severity(node.severity)
                    .service(node.service)
                    .sampleMessages(node.samples)
                    .build());
        }

        // Update from tree (templates may have been merged)
        for (List<TemplateNode> nodes : templateTree.values()) {
            for (TemplateNode node : nodes) {
                // Find in results and update count
                results.stream()
                        .filter(r -> r.getClusterId() == node.id)
                        .findFirst()
                        .ifPresent(r -> {
                            r.setOccurrenceCount(node.count);
                            r.setTemplate(String.join(" ", node.template));
                        });
            }
        }

        results.sort(Comparator.comparingDouble(LogCluster::getSeverityScore).reversed());
        return results;
    }

    private String[] tokenize(String line) {
        // Remove timestamp prefix (common pattern: "2024-01-15 10:30:45,123 INFO ...")
        String cleaned = line.replaceFirst("^\\d{4}[-/]\\d{2}[-/]\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}[.,]?\\d*\\s*", "");
        return cleaned.split("\\s+");
    }

    private String[] normalizeTokens(String[] tokens) {
        String[] result = new String[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            boolean replaced = false;
            for (Pattern p : VARIABLE_PATTERNS) {
                if (p.matcher(token).matches()) {
                    result[i] = "<*>";
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                // Replace pure numbers
                if (token.matches("\\d+")) {
                    result[i] = "<*>";
                } else {
                    result[i] = token;
                }
            }
        }
        return result;
    }

    private TemplateNode findMatchingTemplate(String[] tokens, List<TemplateNode> candidates) {
        TemplateNode bestMatch = null;
        double bestSim = 0;

        for (TemplateNode candidate : candidates) {
            if (candidate.template.length != tokens.length) continue;
            double sim = similarity(tokens, candidate.template);
            if (sim > similarityThreshold && sim > bestSim) {
                bestSim = sim;
                bestMatch = candidate;
            }
        }
        return bestMatch;
    }

    private double similarity(String[] a, String[] b) {
        if (a.length != b.length) return 0;
        int matches = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals(b[i]) || a[i].equals("<*>") || b[i].equals("<*>")) {
                matches++;
            }
        }
        return (double) matches / a.length;
    }

    private String extractSeverity(String line) {
        if (line.contains("FATAL") || line.contains("CRITICAL")) return "CRITICAL";
        if (line.contains("ERROR")) return "WARNING";
        if (line.contains("WARN")) return "WARNING";
        if (line.contains("Exception") || line.contains("OutOfMemory")) return "CRITICAL";
        return "INFO";
    }

    private String extractService(String line) {
        if (line.contains("namenode") || line.contains("NameNode")) return "HDFS";
        if (line.contains("datanode") || line.contains("DataNode")) return "HDFS";
        if (line.contains("resourcemanager") || line.contains("ResourceManager")) return "YARN";
        if (line.contains("nodemanager") || line.contains("NodeManager")) return "YARN";
        if (line.contains("hbase") || line.contains("HBase") || line.contains("regionserver")) return "HBase";
        if (line.contains("hive") || line.contains("HiveServer")) return "Hive";
        if (line.contains("kafka") || line.contains("Kafka")) return "Kafka";
        if (line.contains("zookeeper") || line.contains("ZooKeeper")) return "ZooKeeper";
        if (line.contains("impala") || line.contains("Impala")) return "Impala";
        return "Unknown";
    }

    private double computeSeverityScore(TemplateNode node) {
        double score = 0;
        // Base on severity
        switch (node.severity) {
            case "CRITICAL": score = 0.8; break;
            case "WARNING": score = 0.5; break;
            default: score = 0.2;
        }
        // Boost by frequency (log scale)
        score += Math.min(0.2, Math.log10(node.count + 1) / 10);
        // Boost for known critical keywords
        String template = String.join(" ", node.template);
        if (template.contains("OutOfMemory")) score = Math.min(1.0, score + 0.3);
        if (template.contains("FATAL")) score = Math.min(1.0, score + 0.2);
        if (template.contains("corruption") || template.contains("Corrupt")) score = Math.min(1.0, score + 0.2);
        return Math.min(1.0, score);
    }

    // --- Internal template node ---
    private static class TemplateNode {
        int id;
        String[] template;
        int count = 1;
        String severity;
        String service;
        List<String> samples = new ArrayList<>();

        TemplateNode(int id, String[] template, String severity, String service) {
            this.id = id;
            this.template = template.clone();
            this.severity = severity;
            this.service = service;
        }

        void updateTemplate(String[] newTokens) {
            for (int i = 0; i < template.length && i < newTokens.length; i++) {
                if (!template[i].equals(newTokens[i])) {
                    template[i] = "<*>";
                }
            }
        }
    }
}
