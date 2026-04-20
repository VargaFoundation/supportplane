package varga.supportplane.dto.response;

import varga.supportplane.model.AuditRun;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AuditRunResponse {

    private Long id;
    private Long clusterId;
    private String clusterName;
    private String triggeredByName;
    private String status;
    private Integer rulesEvaluated;
    private Integer findingsCount;
    private Map<String, Object> summary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static AuditRunResponse from(AuditRun run) {
        AuditRunResponse r = new AuditRunResponse();
        r.setId(run.getId());
        if (run.getCluster() != null) {
            r.setClusterId(run.getCluster().getId());
            r.setClusterName(run.getCluster().getName());
        }
        if (run.getTriggeredBy() != null) {
            r.setTriggeredByName(run.getTriggeredBy().getFullName());
        }
        r.setStatus(run.getStatus());
        r.setRulesEvaluated(run.getRulesEvaluated());
        r.setFindingsCount(run.getFindingsCount());
        r.setSummary(run.getSummary());
        r.setStartedAt(run.getStartedAt());
        r.setCompletedAt(run.getCompletedAt());
        return r;
    }
}
