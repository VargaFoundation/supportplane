package com.odp.supportplane.controller;

import com.odp.supportplane.dto.request.NotificationConfigRequest;
import com.odp.supportplane.model.Notification;
import com.odp.supportplane.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/config")
    public ResponseEntity<List<Notification>> listConfigs() {
        return ResponseEntity.ok(notificationService.getConfigs());
    }

    @PostMapping("/config")
    public ResponseEntity<Notification> createConfig(
            @Valid @RequestBody NotificationConfigRequest request) {
        Notification notification = notificationService.create(
                request.getType(), request.getChannel(), request.getConfig());
        return ResponseEntity.ok(notification);
    }

    @PutMapping("/config/{id}")
    public ResponseEntity<Notification> updateConfig(@PathVariable Long id,
                                                       @RequestBody NotificationConfigRequest request) {
        Notification notification = notificationService.update(
                id, request.getType(), request.getChannel(),
                request.getConfig(), request.getEnabled());
        return ResponseEntity.ok(notification);
    }

    @DeleteMapping("/config/{id}")
    public ResponseEntity<?> deleteConfig(@PathVariable Long id) {
        notificationService.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
