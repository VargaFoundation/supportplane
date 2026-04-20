package varga.supportplane.controller;

import varga.supportplane.dto.request.CreateTicketRequest;
import varga.supportplane.dto.response.TicketResponse;
import varga.supportplane.model.Ticket;
import varga.supportplane.model.TicketComment;
import varga.supportplane.service.TicketService;
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
    public ResponseEntity<List<TicketResponse>> listTickets(
            @RequestParam(required = false) String status) {
        List<TicketResponse> tickets = ticketService.getTickets(status).stream()
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
                                                        @RequestBody Map<String, Object> body) {
        Ticket ticket;
        if (body.containsKey("userId") && body.get("userId") != null) {
            ticket = ticketService.assign(id, ((Number) body.get("userId")).longValue());
        } else if (body.containsKey("assignedTo") && body.get("assignedTo") != null) {
            ticket = ticketService.assignByEmail(id, (String) body.get("assignedTo"));
        } else {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(@PathVariable Long id,
                                                        @RequestBody Map<String, String> body) {
        var ticket = ticketService.updateStatus(id, body.get("status"));
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id,
                                         @RequestBody Map<String, String> body) {
        TicketComment comment = ticketService.addComment(id, null, body.get("content"));
        return ResponseEntity.ok(buildCommentResponse(comment));
    }

    @PostMapping("/{id}/comments/upload")
    public ResponseEntity<?> addCommentWithAttachment(
            @PathVariable Long id,
            @RequestParam("content") String content,
            @RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        TicketComment comment = ticketService.addCommentWithAttachment(id, null, content, file);
        return ResponseEntity.ok(buildCommentResponse(comment));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long id) {
        List<Map<String, Object>> comments = ticketService.getComments(id).stream()
                .map(this::buildCommentResponse)
                .toList();
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/comments/{commentId}/attachment")
    public ResponseEntity<?> downloadAttachment(@PathVariable Long commentId) {
        return ticketService.getCommentById(commentId)
                .filter(c -> c.getAttachmentFilepath() != null)
                .map(c -> {
                    try {
                        java.nio.file.Path path = java.nio.file.Path.of(c.getAttachmentFilepath());
                        if (!java.nio.file.Files.exists(path)) return ResponseEntity.notFound().build();
                        var resource = new org.springframework.core.io.InputStreamResource(
                                new java.io.FileInputStream(path.toFile()));
                        return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + c.getAttachmentFilename() + "\"")
                                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                                .body(resource);
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError().build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> buildCommentResponse(TicketComment comment) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", comment.getId());
        map.put("content", comment.getContent());
        if (comment.getAuthor() != null) {
            map.put("authorName", comment.getAuthor().getFullName());
        }
        if (comment.getAttachmentFilename() != null) {
            map.put("attachmentFilename", comment.getAttachmentFilename());
            map.put("attachmentSize", comment.getAttachmentSize());
        }
        map.put("createdAt", comment.getCreatedAt().toString());
        return map;
    }
}
