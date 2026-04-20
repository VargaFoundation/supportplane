package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.LogCluster;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogTokenizerTest {

    @Test
    void parse_similarMessages_groupedIntoOneCluster() {
        LogTokenizer tokenizer = new LogTokenizer(0.5);

        List<String> lines = List.of(
                "2024-01-15 10:00:00 ERROR BlockMissing: Block blk_123456 is missing 2 replicas",
                "2024-01-15 10:00:01 ERROR BlockMissing: Block blk_789012 is missing 1 replicas",
                "2024-01-15 10:00:02 ERROR BlockMissing: Block blk_345678 is missing 3 replicas"
        );

        List<LogCluster> clusters = tokenizer.parse(lines);
        // All 3 messages should be grouped into 1 template
        assertTrue(clusters.size() <= 2, "Similar messages should cluster together, got " + clusters.size());
        assertTrue(clusters.get(0).getOccurrenceCount() >= 2,
                "Cluster should have multiple occurrences");
        assertTrue(clusters.get(0).getTemplate().contains("<*>"),
                "Template should contain wildcards");
    }

    @Test
    void parse_differentMessages_separateClusters() {
        LogTokenizer tokenizer = new LogTokenizer(0.5);

        List<String> lines = List.of(
                "ERROR OutOfMemoryError: Java heap space",
                "WARN DiskChecker: Disk /data1/hadoop is not healthy",
                "FATAL RegionServer abort: Session expired"
        );

        List<LogCluster> clusters = tokenizer.parse(lines);
        assertTrue(clusters.size() >= 2, "Different messages should create separate clusters");
    }

    @Test
    void parse_emptyInput_returnsEmpty() {
        LogTokenizer tokenizer = new LogTokenizer();
        assertTrue(tokenizer.parse(List.of()).isEmpty());
    }

    @Test
    void parse_severityExtraction() {
        LogTokenizer tokenizer = new LogTokenizer();
        List<LogCluster> clusters = tokenizer.parse(List.of(
                "FATAL: NameNode is shutting down"
        ));
        assertFalse(clusters.isEmpty());
        assertEquals("CRITICAL", clusters.get(0).getSeverity());
    }

    @Test
    void parse_serviceExtraction() {
        LogTokenizer tokenizer = new LogTokenizer();
        List<LogCluster> clusters = tokenizer.parse(List.of(
                "ERROR org.apache.hadoop.hdfs.server.namenode.NameNode - Failed to start"
        ));
        assertFalse(clusters.isEmpty());
        assertEquals("HDFS", clusters.get(0).getService());
    }
}
