package varga.supportplane.ai.algorithm;

import varga.supportplane.ai.model.TuningRecommendation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BayesianOptimizerTest {

    @Test
    void classifyWorkload_highCpu_cpuBound() {
        BayesianOptimizer opt = new BayesianOptimizer();
        assertEquals(BayesianOptimizer.WorkloadProfile.CPU_BOUND,
                opt.classifyWorkload(85, 5, 60, 0));
    }

    @Test
    void classifyWorkload_highIowait_ioBound() {
        BayesianOptimizer opt = new BayesianOptimizer();
        assertEquals(BayesianOptimizer.WorkloadProfile.IO_BOUND,
                opt.classifyWorkload(40, 30, 60, 0));
    }

    @Test
    void classifyWorkload_swapActive_memoryBound() {
        BayesianOptimizer opt = new BayesianOptimizer();
        assertEquals(BayesianOptimizer.WorkloadProfile.MEMORY_BOUND,
                opt.classifyWorkload(40, 5, 60, 1024));
    }

    @Test
    void classifyWorkload_moderate_mixed() {
        BayesianOptimizer opt = new BayesianOptimizer();
        assertEquals(BayesianOptimizer.WorkloadProfile.MIXED,
                opt.classifyWorkload(30, 5, 50, 0));
    }

    @Test
    void recommend_ioBound_suggestsHigherCache() {
        BayesianOptimizer opt = new BayesianOptimizer();
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("memory.total_gb", 128.0);
        metrics.put("cpu.cores", 16.0);
        metrics.put("jvm.heap_max_gb", 8.0);

        Map<String, String> config = new HashMap<>();
        config.put("hfile.block.cache.size", "0.4");
        config.put("yarn.nodemanager.resource.memory-mb", "65536");

        List<TuningRecommendation> recs = opt.recommend(
                BayesianOptimizer.WorkloadProfile.IO_BOUND, metrics, config);

        assertFalse(recs.isEmpty(), "Should generate recommendations");
        assertTrue(recs.stream().anyMatch(r -> r.getComponent().equals("HBase")),
                "Should include HBase recommendations for I/O-bound");
    }

    @Test
    void recommend_memoryBound_suggestsLowSwappiness() {
        BayesianOptimizer opt = new BayesianOptimizer();
        Map<String, Double> metrics = Map.of("memory.total_gb", 64.0, "cpu.cores", 8.0, "jvm.heap_max_gb", 8.0);
        Map<String, String> config = Map.of("vm.swappiness", "60");

        List<TuningRecommendation> recs = opt.recommend(
                BayesianOptimizer.WorkloadProfile.MEMORY_BOUND, metrics, config);

        assertTrue(recs.stream().anyMatch(r ->
                        r.getParameter().equals("vm.swappiness") && r.getSuggestedValue().equals("1")),
                "Should recommend swappiness=1 for memory-bound");
    }
}
