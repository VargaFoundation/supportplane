package varga.supportplane.ai;

import varga.supportplane.model.Cluster;
import varga.supportplane.repository.ClusterRepository;
import varga.supportplane.repository.MetricHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Scheduler — runs analysis automatically on clusters with recent bundles.
 *
 * Also handles metric history retention (cleanup old data beyond 90 days).
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class AIScheduler {

    private final ClusterRepository clusterRepository;
    private final MetricHistoryRepository metricHistoryRepository;
    private final AIOrchestrator orchestrator;

    /**
     * Analyze clusters that received a bundle in the last hour.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300_000)
    public void analyzeActiveClusters() {
        List<Cluster> activeClusters = clusterRepository.findByStatus("ACTIVE");

        for (Cluster cluster : activeClusters) {
            if (cluster.getLastBundleAt() == null) continue;
            if (cluster.getLastBundleAt().isBefore(LocalDateTime.now().minusHours(1))) continue;

            try {
                long historyCount = metricHistoryRepository.countByClusterId(cluster.getId());
                if (historyCount < 5) {
                    log.debug("Skipping AI for cluster {} — only {} snapshots (need 5+)",
                            cluster.getClusterId(), historyCount);
                    continue;
                }

                orchestrator.analyzeCluster(cluster.getId());
                log.info("AI analysis completed for cluster {} ({} snapshots)",
                        cluster.getClusterId(), historyCount);
            } catch (Exception e) {
                log.warn("AI analysis failed for cluster {}: {}", cluster.getClusterId(), e.getMessage());
            }
        }
    }

    /**
     * Cleanup metric history older than 90 days.
     * Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldMetricHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        List<Cluster> allClusters = clusterRepository.findAll();

        for (Cluster cluster : allClusters) {
            try {
                metricHistoryRepository.deleteByClusterIdAndCollectedAtBefore(
                        cluster.getId(), cutoff);
            } catch (Exception e) {
                log.warn("Failed to cleanup metric history for cluster {}: {}",
                        cluster.getClusterId(), e.getMessage());
            }
        }
        log.info("Metric history cleanup completed (removed data before {})", cutoff);
    }
}
