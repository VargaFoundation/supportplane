package varga.supportplane.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class CreateRuleRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String category;

    private String subcategory;

    @NotBlank
    private String component;

    private String threat;
    private String vulnerability;
    private String asset;
    private String impact;
    private String defaultLikelihood;
    private String defaultSeverity;
    private String recommendationsText;
    private Map<String, Object> condition;
}
