package varga.supportplane.controller;

import varga.supportplane.ai.AIOrchestrator;
import varga.supportplane.dto.response.AIAnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI Controller — exposes AI analysis endpoints.
 *
 * Delegates to AIOrchestrator which coordinates all 4 engines
 * with real historical data from metric_history.
 */
@RestController
@RequestMapping("/api/v1/clusters/{clusterId}/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIOrchestrator orchestrator;

    @PostMapping("/analyze")
    public ResponseEntity<AIAnalysisResponse> runFullAnalysis(@PathVariable Long clusterId) {
        return ResponseEntity.ok(orchestrator.analyzeCluster(clusterId));
    }
}
