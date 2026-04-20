package varga.supportplane.dto.response;

import varga.supportplane.model.Ticket;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TicketResponse {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private Long clusterId;
    private String clusterName;
    private String createdByName;
    private String assignedToName;
    private String tenantName;
    private LocalDateTime slaDeadline;
    private Boolean slaBreached;
    private Long slaRemainingMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TicketResponse from(Ticket ticket) {
        TicketResponse r = new TicketResponse();
        r.setId(ticket.getId());
        r.setTitle(ticket.getTitle());
        r.setDescription(ticket.getDescription());
        r.setStatus(ticket.getStatus());
        r.setPriority(ticket.getPriority());
        if (ticket.getCluster() != null) {
            r.setClusterId(ticket.getCluster().getId());
            r.setClusterName(ticket.getCluster().getName());
        }
        if (ticket.getCreatedBy() != null) {
            r.setCreatedByName(ticket.getCreatedBy().getFullName());
        }
        if (ticket.getAssignedTo() != null) {
            r.setAssignedToName(ticket.getAssignedTo().getFullName());
        }
        if (ticket.getTenant() != null) {
            r.setTenantName(ticket.getTenant().getName());
        }
        r.setSlaDeadline(ticket.getSlaDeadline());
        r.setSlaBreached(ticket.getSlaBreached());
        if (ticket.getSlaDeadline() != null && !"CLOSED".equals(ticket.getStatus()) && !"RESOLVED".equals(ticket.getStatus())) {
            long minutes = java.time.Duration.between(LocalDateTime.now(), ticket.getSlaDeadline()).toMinutes();
            r.setSlaRemainingMinutes(minutes);
        }
        r.setCreatedAt(ticket.getCreatedAt());
        r.setUpdatedAt(ticket.getUpdatedAt());
        return r;
    }
}
