package com.odp.supportplane.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AttachClusterRequest {
    @NotBlank
    private String clusterId;

    private String name;
}
