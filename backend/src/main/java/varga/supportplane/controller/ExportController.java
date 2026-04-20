package varga.supportplane.controller;

import varga.supportplane.model.Ticket;
import varga.supportplane.service.ClusterService;
import varga.supportplane.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ExportController {

    private final TicketService ticketService;
    private final ClusterService clusterService;

    @GetMapping("/tickets")
    public ResponseEntity<String> exportTickets() {
        var sb = new StringBuilder();
        sb.append("ID,Title,Status,Priority,Tenant,Cluster,Assigned To,SLA Deadline,SLA Breached,Created,Updated\n");

        for (Ticket t : ticketService.getTickets()) {
            sb.append(t.getId()).append(',');
            sb.append(csvEscape(t.getTitle())).append(',');
            sb.append(t.getStatus()).append(',');
            sb.append(t.getPriority()).append(',');
            sb.append(t.getTenant() != null ? csvEscape(t.getTenant().getName()) : "").append(',');
            sb.append(t.getCluster() != null ? csvEscape(t.getCluster().getName()) : "").append(',');
            sb.append(t.getAssignedTo() != null ? csvEscape(t.getAssignedTo().getFullName()) : "").append(',');
            sb.append(t.getSlaDeadline() != null ? t.getSlaDeadline().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "").append(',');
            sb.append(t.getSlaBreached() != null && t.getSlaBreached() ? "YES" : "NO").append(',');
            sb.append(t.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append(',');
            sb.append(t.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            sb.append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tickets.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sb.toString());
    }

    @GetMapping("/clusters")
    public ResponseEntity<String> exportClusters() {
        var sb = new StringBuilder();
        sb.append("ID,Name,Cluster ID,Status,Tenant,OTP Validated,Last Bundle,Created\n");

        for (var c : clusterService.getClusters()) {
            sb.append(c.getId()).append(',');
            sb.append(csvEscape(c.getName())).append(',');
            sb.append(c.getClusterId()).append(',');
            sb.append(c.getStatus()).append(',');
            sb.append(c.getTenant() != null ? csvEscape(c.getTenant().getName()) : "").append(',');
            sb.append(c.getOtpValidated() != null && c.getOtpValidated() ? "YES" : "NO").append(',');
            sb.append(c.getLastBundleAt() != null ? c.getLastBundleAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "").append(',');
            sb.append(c.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            sb.append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"clusters.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sb.toString());
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
