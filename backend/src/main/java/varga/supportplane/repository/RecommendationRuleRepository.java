package varga.supportplane.repository;

import varga.supportplane.model.RecommendationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationRuleRepository extends JpaRepository<RecommendationRule, Long> {

    List<RecommendationRule> findByEnabledTrueOrderByCategoryAscComponentAsc();

    List<RecommendationRule> findByComponentOrderByCategoryAsc(String component);

    List<RecommendationRule> findByCategoryOrderByComponentAsc(String category);

    Optional<RecommendationRule> findByCode(String code);

    boolean existsByCode(String code);
}
