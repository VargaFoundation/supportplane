package com.odp.supportplane.service;

import com.odp.supportplane.config.AccessControl;
import com.odp.supportplane.model.RecommendationRule;
import com.odp.supportplane.repository.RecommendationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecommendationRuleService {

    private final RecommendationRuleRepository ruleRepository;

    public List<RecommendationRule> getAll() {
        return ruleRepository.findAll();
    }

    public List<RecommendationRule> getEnabled() {
        return ruleRepository.findByEnabledTrueOrderByCategoryAscComponentAsc();
    }

    public List<RecommendationRule> getByCategory(String category) {
        return ruleRepository.findByCategoryOrderByComponentAsc(category);
    }

    public List<RecommendationRule> getByComponent(String component) {
        return ruleRepository.findByComponentOrderByCategoryAsc(component);
    }

    public Optional<RecommendationRule> findById(Long id) {
        return ruleRepository.findById(id);
    }

    public Optional<RecommendationRule> findByCode(String code) {
        return ruleRepository.findByCode(code);
    }

    @Transactional
    public RecommendationRule create(String code, String title, String description,
                                      String category, String subcategory, String component,
                                      String threat, String vulnerability, String asset,
                                      String impact, String defaultLikelihood, String defaultSeverity,
                                      String recommendationsText, Map<String, Object> condition) {
        AccessControl.requireOperator();

        if (ruleRepository.existsByCode(code)) {
            throw new RuntimeException("Rule with code '" + code + "' already exists");
        }

        RecommendationRule rule = RecommendationRule.builder()
                .code(code)
                .title(title)
                .description(description)
                .category(category)
                .subcategory(subcategory)
                .component(component)
                .threat(threat)
                .vulnerability(vulnerability)
                .asset(asset)
                .impact(impact)
                .defaultLikelihood(defaultLikelihood != null ? defaultLikelihood : "MEDIUM")
                .defaultSeverity(defaultSeverity != null ? defaultSeverity : "WARNING")
                .recommendationsText(recommendationsText)
                .condition(condition)
                .build();
        return ruleRepository.save(rule);
    }

    @Transactional
    public RecommendationRule update(Long id, String code, String title, String description,
                                      String category, String subcategory, String component,
                                      String threat, String vulnerability, String asset,
                                      String impact, String defaultLikelihood, String defaultSeverity,
                                      String recommendationsText, Map<String, Object> condition,
                                      Boolean enabled) {
        AccessControl.requireOperator();

        RecommendationRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found"));

        if (code != null) rule.setCode(code);
        if (title != null) rule.setTitle(title);
        if (description != null) rule.setDescription(description);
        if (category != null) rule.setCategory(category);
        if (subcategory != null) rule.setSubcategory(subcategory);
        if (component != null) rule.setComponent(component);
        if (threat != null) rule.setThreat(threat);
        if (vulnerability != null) rule.setVulnerability(vulnerability);
        if (asset != null) rule.setAsset(asset);
        if (impact != null) rule.setImpact(impact);
        if (defaultLikelihood != null) rule.setDefaultLikelihood(defaultLikelihood);
        if (defaultSeverity != null) rule.setDefaultSeverity(defaultSeverity);
        if (recommendationsText != null) rule.setRecommendationsText(recommendationsText);
        if (condition != null) rule.setCondition(condition);
        if (enabled != null) rule.setEnabled(enabled);

        return ruleRepository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        AccessControl.requireOperator();
        ruleRepository.deleteById(id);
    }

    @Transactional
    public RecommendationRule toggleEnabled(Long id) {
        AccessControl.requireOperator();
        RecommendationRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found"));
        rule.setEnabled(!rule.getEnabled());
        return ruleRepository.save(rule);
    }
}
