package com.odp.supportplane.controller;

import com.odp.supportplane.dto.response.AuditReportResponse;
import com.odp.supportplane.dto.response.AuditRunResponse;
import com.odp.supportplane.dto.response.ComponentSummaryResponse;
import com.odp.supportplane.service.RecommendationEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationEngineController {

    private final RecommendationEngineService engineService;

    @PostMapping("/clusters/{clusterId}/evaluate")
    public ResponseEntity<AuditRunResponse> evaluateCluster(@PathVariable Long clusterId) {
        var run = engineService.evaluateCluster(clusterId, null);
        return ResponseEntity.ok(AuditRunResponse.from(run));
    }

    @GetMapping("/clusters/{clusterId}/audit-report")
    public ResponseEntity<AuditReportResponse> getAuditReport(@PathVariable Long clusterId) {
        return ResponseEntity.ok(engineService.getAuditReport(clusterId));
    }

    @GetMapping("/clusters/{clusterId}/component-summary")
    public ResponseEntity<List<ComponentSummaryResponse>> getComponentSummary(
            @PathVariable Long clusterId) {
        return ResponseEntity.ok(engineService.getComponentSummary(clusterId));
    }

    @GetMapping("/clusters/{clusterId}/audit-runs")
    public ResponseEntity<List<AuditRunResponse>> getAuditRuns(@PathVariable Long clusterId) {
        List<AuditRunResponse> runs = engineService.getAuditRuns(clusterId).stream()
                .map(AuditRunResponse::from)
                .toList();
        return ResponseEntity.ok(runs);
    }
}
