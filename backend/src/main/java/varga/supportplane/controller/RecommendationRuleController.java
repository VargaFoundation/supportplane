package varga.supportplane.controller;

import varga.supportplane.dto.request.CreateRuleRequest;
import varga.supportplane.dto.request.UpdateRuleRequest;
import varga.supportplane.dto.response.RecommendationRuleResponse;
import varga.supportplane.service.RecommendationRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendation-rules")
@RequiredArgsConstructor
public class RecommendationRuleController {

    private final RecommendationRuleService ruleService;

    @GetMapping
    public ResponseEntity<List<RecommendationRuleResponse>> listRules(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String component) {
        List<RecommendationRuleResponse> rules;
        if (category != null) {
            rules = ruleService.getByCategory(category).stream()
                    .map(RecommendationRuleResponse::from).toList();
        } else if (component != null) {
            rules = ruleService.getByComponent(component).stream()
                    .map(RecommendationRuleResponse::from).toList();
        } else {
            rules = ruleService.getAll().stream()
                    .map(RecommendationRuleResponse::from).toList();
        }
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecommendationRuleResponse> getRule(@PathVariable Long id) {
        return ruleService.findById(id)
                .map(RecommendationRuleResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RecommendationRuleResponse> createRule(
            @Valid @RequestBody CreateRuleRequest request) {
        var rule = ruleService.create(
                request.getCode(), request.getTitle(), request.getDescription(),
                request.getCategory(), request.getSubcategory(), request.getComponent(),
                request.getThreat(), request.getVulnerability(), request.getAsset(),
                request.getImpact(), request.getDefaultLikelihood(), request.getDefaultSeverity(),
                request.getRecommendationsText(), request.getCondition());
        return ResponseEntity.ok(RecommendationRuleResponse.from(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecommendationRuleResponse> updateRule(
            @PathVariable Long id, @RequestBody UpdateRuleRequest request) {
        var rule = ruleService.update(id,
                request.getCode(), request.getTitle(), request.getDescription(),
                request.getCategory(), request.getSubcategory(), request.getComponent(),
                request.getThreat(), request.getVulnerability(), request.getAsset(),
                request.getImpact(), request.getDefaultLikelihood(), request.getDefaultSeverity(),
                request.getRecommendationsText(), request.getCondition(), request.getEnabled());
        return ResponseEntity.ok(RecommendationRuleResponse.from(rule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        ruleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<RecommendationRuleResponse> toggleRule(@PathVariable Long id) {
        var rule = ruleService.toggleEnabled(id);
        return ResponseEntity.ok(RecommendationRuleResponse.from(rule));
    }
}
