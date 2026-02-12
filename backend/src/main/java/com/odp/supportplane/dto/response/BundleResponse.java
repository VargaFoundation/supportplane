package com.odp.supportplane.dto.response;

import com.odp.supportplane.model.Bundle;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class BundleResponse {
    private Long id;
    private String bundleId;
    private String filename;
    private Long sizeBytes;
    private Map<String, Object> metadata;
    private String analysisSummary;
    private LocalDateTime receivedAt;

    public static BundleResponse from(Bundle bundle) {
        BundleResponse r = new BundleResponse();
        r.setId(bundle.getId());
        r.setBundleId(bundle.getBundleId());
        r.setFilename(bundle.getFilename());
        r.setSizeBytes(bundle.getSizeBytes());
        r.setMetadata(bundle.getMetadata());
        r.setAnalysisSummary(bundle.getAnalysisSummary());
        r.setReceivedAt(bundle.getReceivedAt());
        return r;
    }
}
