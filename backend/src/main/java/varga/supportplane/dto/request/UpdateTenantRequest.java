package varga.supportplane.dto.request;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateTenantRequest {
    private String name;
    private String clientName;
    private String supportLevel;
    private String contractReference;
    private String contractFramework;
    private LocalDate contractEndDate;
    private String notes;
    private Boolean active;
}
