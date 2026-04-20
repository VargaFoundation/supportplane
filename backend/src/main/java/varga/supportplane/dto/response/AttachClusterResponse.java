package varga.supportplane.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AttachClusterResponse {
    private Long clusterId;
    private String otpCode;
    private String message;
}
