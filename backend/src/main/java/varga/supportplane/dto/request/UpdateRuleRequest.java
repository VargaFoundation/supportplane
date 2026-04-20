package varga.supportplane.dto.request;

import lombok.Data;
import java.util.Map;

@Data
public class UpdateRuleRequest {

    private String code;
    private String title;
    private String description;
    private String category;
    private String subcategory;
    private String component;
    private String threat;
    private String vulnerability;
    private String asset;
    private String impact;
    private String defaultLikelihood;
    private String defaultSeverity;
    private String recommendationsText;
    private Map<String, Object> condition;
    private Boolean enabled;
}
