package com.odp.supportplane.controller;

import com.odp.supportplane.dto.request.CreateRecommendationRequest;
import com.odp.supportplane.dto.response.RecommendationResponse;
import com.odp.supportplane.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/clusters/{clusterId}/recommendations")
    public ResponseEntity<List<RecommendationResponse>> getRecommendations(
            @PathVariable Long clusterId) {
        List<RecommendationResponse> recs = recommendationService.getForCluster(clusterId).stream()
                .map(RecommendationResponse::from)
                .toList();
        return ResponseEntity.ok(recs);
    }

    @PostMapping("/recommendations")
    public ResponseEntity<RecommendationResponse> createRecommendation(
            @Valid @RequestBody CreateRecommendationRequest request) {
        var rec = recommendationService.create(
                request.getClusterId(), request.getTitle(),
                request.getDescription(), request.getSeverity(), null);
        return ResponseEntity.ok(RecommendationResponse.from(rec));
    }

    @PutMapping("/recommendations/{id}/validate")
    public ResponseEntity<RecommendationResponse> validateRecommendation(@PathVariable Long id) {
        var rec = recommendationService.validate(id);
        return ResponseEntity.ok(RecommendationResponse.from(rec));
    }

    @PutMapping("/recommendations/{id}/deliver")
    public ResponseEntity<RecommendationResponse> deliverRecommendation(@PathVariable Long id) {
        var rec = recommendationService.deliver(id);
        return ResponseEntity.ok(RecommendationResponse.from(rec));
    }
}
