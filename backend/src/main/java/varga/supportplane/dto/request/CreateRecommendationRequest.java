package varga.supportplane.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRecommendationRequest {
    @NotNull
    private Long clusterId;

    @NotBlank
    private String title;

    private String description;
    private String severity;
}
