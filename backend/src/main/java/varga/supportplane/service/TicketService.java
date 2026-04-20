package varga.supportplane.service;

import varga.supportplane.config.AccessControl;
import varga.supportplane.config.TenantContext;
import varga.supportplane.model.*;
import varga.supportplane.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository commentRepository;
    private final TenantRepository tenantRepository;
    private final ClusterRepository clusterRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationDispatcher notificationDispatcher;

    public List<Ticket> getTickets(String status) {
        if (TenantContext.isOperator()) {
            if (status != null && !status.isBlank()) {
                return ticketRepository.findByStatusOrderByCreatedAtDesc(status);
            }
            return ticketRepository.findAllByOrderByCreatedAtDesc();
        }
        Tenant tenant = getCurrentTenant();
        if (status != null && !status.isBlank()) {
            return ticketRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenant.getId(), status);
        }
        return ticketRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId());
    }

    public List<Ticket> getTickets() {
        return getTickets(null);
    }

    public Optional<Ticket> findById(Long id) {
        Optional<Ticket> opt = ticketRepository.findById(id);
        if (opt.isPresent() && !TenantContext.isOperator()) {
            Ticket ticket = opt.get();
            if (ticket.getTenant() != null) {
                Tenant caller = getCurrentTenant();
                if (!ticket.getTenant().getId().equals(caller.getId())) {
                    return Optional.empty();
                }
            }
        }
        return opt;
    }

    @Transactional
    public Ticket create(String title, String description, Long clusterId,
                          String priority, Long createdById) {
        Tenant tenant = getCurrentTenant();
        Cluster cluster = clusterId != null ? clusterRepository.findById(clusterId).orElse(null) : null;
        User createdBy = createdById != null ? userRepository.findById(createdById).orElse(null) : null;

        String effectivePriority = priority != null ? priority : "MEDIUM";

        Ticket ticket = Ticket.builder()
                .tenant(tenant)
                .cluster(cluster)
                .title(title)
                .description(description)
                .priority(effectivePriority)
                .slaDeadline(computeSlaDeadline(tenant.getSupportLevel(), effectivePriority))
                .createdBy(createdBy)
                .build();
        ticket = ticketRepository.save(ticket);
        auditService.log("TICKET_CREATED", "TICKET", String.valueOf(ticket.getId()), title, "Priority: " + effectivePriority);
        notificationDispatcher.dispatch(tenant, "TICKET_CREATED",
                "New ticket: " + title, "Priority: " + effectivePriority);
        return ticket;
    }

    @Transactional
    public Ticket updateStatus(Long ticketId, String status) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        ticket.setStatus(status);
        ticket = ticketRepository.save(ticket);
        auditService.log("TICKET_STATUS_CHANGED", "TICKET", String.valueOf(ticketId), ticket.getTitle(), "New status: " + status);
        if (ticket.getTenant() != null) {
            notificationDispatcher.dispatch(ticket.getTenant(), "TICKET_UPDATED",
                    "Ticket updated: " + ticket.getTitle(), "Status changed to " + status);
        }
        return ticket;
    }

    @Transactional
    public Ticket assign(Long ticketId, Long userId) {
        AccessControl.requireAdminOrOperator();
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        ticket.setAssignedTo(assignee);
        ticket.setStatus("ASSIGNED");
        ticket = ticketRepository.save(ticket);
        auditService.log("TICKET_ASSIGNED", "TICKET", String.valueOf(ticketId), ticket.getTitle(), "Assigned to: " + assignee.getEmail());
        return ticket;
    }

    @Transactional
    public Ticket assignByEmail(Long ticketId, String email) {
        User assignee = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        return assign(ticketId, assignee.getId());
    }

    @Transactional
    public TicketComment addComment(Long ticketId, Long authorId, String content) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        User author = authorId != null ? userRepository.findById(authorId).orElse(null) : null;

        TicketComment comment = TicketComment.builder()
                .ticket(ticket)
                .author(author)
                .content(content)
                .build();
        return commentRepository.save(comment);
    }

    @Transactional
    public TicketComment addCommentWithAttachment(Long ticketId, Long authorId, String content,
                                                    org.springframework.web.multipart.MultipartFile file) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        User author = authorId != null ? userRepository.findById(authorId).orElse(null) : null;

        TicketComment.TicketCommentBuilder builder = TicketComment.builder()
                .ticket(ticket)
                .author(author)
                .content(content);

        if (file != null && !file.isEmpty()) {
            try {
                java.nio.file.Path dir = java.nio.file.Path.of("/var/lib/supportplane/attachments");
                java.nio.file.Files.createDirectories(dir);
                String filename = file.getOriginalFilename();
                java.nio.file.Path filePath = dir.resolve(ticketId + "_" + System.currentTimeMillis() + "_" + filename);
                file.transferTo(filePath);
                builder.attachmentFilename(filename);
                builder.attachmentFilepath(filePath.toString());
                builder.attachmentSize(file.getSize());
            } catch (Exception e) {
                // Non-critical: comment still saved without attachment
            }
        }

        return commentRepository.save(builder.build());
    }

    public java.util.Optional<TicketComment> getCommentById(Long commentId) {
        return commentRepository.findById(commentId);
    }

    public List<TicketComment> getComments(Long ticketId) {
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    /**
     * Compute SLA deadline based on tenant support level and ticket priority.
     * Matrix (hours):
     *                CRITICAL  HIGH  MEDIUM  LOW
     * CRITICAL(sla)     1       2      4      8
     * PREMIUM           2       4      8     24
     * STANDARD          4       8     24     48
     * BASIC/null        8      24     48     72
     */
    private LocalDateTime computeSlaDeadline(String supportLevel, String priority) {
        int hours = switch (priority) {
            case "CRITICAL" -> switch (supportLevel != null ? supportLevel : "") {
                case "CRITICAL" -> 1;
                case "PREMIUM" -> 2;
                case "STANDARD" -> 4;
                default -> 8;
            };
            case "HIGH" -> switch (supportLevel != null ? supportLevel : "") {
                case "CRITICAL" -> 2;
                case "PREMIUM" -> 4;
                case "STANDARD" -> 8;
                default -> 24;
            };
            case "MEDIUM" -> switch (supportLevel != null ? supportLevel : "") {
                case "CRITICAL" -> 4;
                case "PREMIUM" -> 8;
                case "STANDARD" -> 24;
                default -> 48;
            };
            default -> switch (supportLevel != null ? supportLevel : "") {
                case "CRITICAL" -> 8;
                case "PREMIUM" -> 24;
                case "STANDARD" -> 48;
                default -> 72;
            };
        };
        return LocalDateTime.now().plusHours(hours);
    }

    private Tenant getCurrentTenant() {
        String tenantId = TenantContext.getTenantId();
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
    }
}
