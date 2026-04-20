package varga.supportplane.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AuditReportResponse {

    private String clusterName;
    private String clusterId;
    private LocalDateTime generatedAt;
    private Map<String, Integer> summary;
    private List<CategoryGroup> categories;

    @Data
    public static class CategoryGroup {
        private String name;
        private List<SubcategoryGroup> subcategories;
        private List<FindingResponse> findings;
    }

    @Data
    public static class SubcategoryGroup {
        private String name;
        private List<FindingResponse> findings;
    }

    @Data
    public static class FindingResponse {
        private Long id;
        private String ruleCode;
        private String title;
        private String description;
        private String findingStatus;
        private String component;
        private String threat;
        private String vulnerability;
        private String asset;
        private String impact;
        private String likelihood;
        private String risk;
        private String severity;
        private String recommendationsText;
        private String status;
    }
}
