package varga.supportplane.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class NotificationConfigRequest {
    @NotBlank
    private String type;

    @NotBlank
    private String channel;

    private Map<String, Object> config;
    private Boolean enabled;
}
