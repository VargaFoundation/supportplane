package com.odp.supportplane.dto.request;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UpdateLicenseRequest {
    private Long tenantId;
    private String tier;
    private Integer maxClusters;
    private Integer maxUsers;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
}
