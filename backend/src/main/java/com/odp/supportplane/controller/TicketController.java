package com.odp.supportplane.controller;

import com.odp.supportplane.dto.request.CreateTicketRequest;
import com.odp.supportplane.dto.response.TicketResponse;
import com.odp.supportplane.model.TicketComment;
import com.odp.supportplane.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<List<TicketResponse>> listTickets() {
        List<TicketResponse> tickets = ticketService.getTickets().stream()
                .map(TicketResponse::from)
                .toList();
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable Long id) {
        return ticketService.findById(id)
                .map(t -> ResponseEntity.ok(TicketResponse.from(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        var ticket = ticketService.create(
                request.getTitle(), request.getDescription(),
                request.getClusterId(), request.getPriority(), null);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateStatus(@PathVariable Long id,
                                                         @RequestBody Map<String, String> body) {
        var ticket = ticketService.updateStatus(id, body.get("status"));
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<TicketResponse> assignTicket(@PathVariable Long id,
                                                        @RequestBody Map<String, Long> body) {
        var ticket = ticketService.assign(id, body.get("userId"));
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        TicketComment comment = ticketService.addComment(id, null, body.get("content"));
        return ResponseEntity.ok(Map.of(
                "id", comment.getId(),
                "content", comment.getContent(),
                "createdAt", comment.getCreatedAt().toString()
        ));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<TicketComment>> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getComments(id));
    }
}
