package varga.supportplane.controller;

import varga.supportplane.model.AuditEvent;
import varga.supportplane.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAuditEvents() {
        List<Map<String, Object>> events = auditService.getEvents().stream()
                .map(e -> Map.<String, Object>of(
                        "id", e.getId(),
                        "actor", e.getActor() != null ? e.getActor() : "",
                        "actorRole", e.getActorRole() != null ? e.getActorRole() : "",
                        "action", e.getAction(),
                        "targetType", e.getTargetType() != null ? e.getTargetType() : "",
                        "targetId", e.getTargetId() != null ? e.getTargetId() : "",
                        "targetLabel", e.getTargetLabel() != null ? e.getTargetLabel() : "",
                        "details", e.getDetails() != null ? e.getDetails() : "",
                        "createdAt", e.getCreatedAt().toString()
                ))
                .toList();
        return ResponseEntity.ok(events);
    }
}
