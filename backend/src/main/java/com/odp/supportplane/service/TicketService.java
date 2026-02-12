package com.odp.supportplane.service;

import com.odp.supportplane.config.TenantContext;
import com.odp.supportplane.model.*;
import com.odp.supportplane.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<Ticket> getTickets() {
        if (TenantContext.isOperator()) {
            return ticketRepository.findAll();
        }
        Tenant tenant = getCurrentTenant();
        return ticketRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId());
    }

    public Optional<Ticket> findById(Long id) {
        return ticketRepository.findById(id);
    }

    @Transactional
    public Ticket create(String title, String description, Long clusterId,
                          String priority, Long createdById) {
        Tenant tenant = getCurrentTenant();
        Cluster cluster = clusterId != null ? clusterRepository.findById(clusterId).orElse(null) : null;
        User createdBy = createdById != null ? userRepository.findById(createdById).orElse(null) : null;

        Ticket ticket = Ticket.builder()
                .tenant(tenant)
                .cluster(cluster)
                .title(title)
                .description(description)
                .priority(priority != null ? priority : "MEDIUM")
                .createdBy(createdBy)
                .build();
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket updateStatus(Long ticketId, String status) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        ticket.setStatus(status);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket assign(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        ticket.setAssignedTo(assignee);
        ticket.setStatus("ASSIGNED");
        return ticketRepository.save(ticket);
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

    public List<TicketComment> getComments(Long ticketId) {
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    private Tenant getCurrentTenant() {
        String tenantId = TenantContext.getTenantId();
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
    }
}
