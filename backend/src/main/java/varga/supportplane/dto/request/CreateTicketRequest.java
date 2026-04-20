package varga.supportplane.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTicketRequest {
    @NotBlank
    private String title;

    private String description;
    private Long clusterId;
    private String priority;
}
