package varga.supportplane.service;

import varga.supportplane.model.Ticket;
import varga.supportplane.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically checks for SLA breaches and marks tickets accordingly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlaCheckScheduler {

    private final TicketRepository ticketRepository;

    @Scheduled(fixedRate = 60000) // Every minute
    @Transactional
    public void checkSlaBreaches() {
        LocalDateTime now = LocalDateTime.now();
        // Find open tickets with SLA deadline passed and not yet marked as breached
        List<Ticket> openTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getSlaDeadline() != null
                        && !Boolean.TRUE.equals(t.getSlaBreached())
                        && t.getSlaDeadline().isBefore(now)
                        && !"CLOSED".equals(t.getStatus())
                        && !"RESOLVED".equals(t.getStatus()))
                .toList();

        for (Ticket ticket : openTickets) {
            ticket.setSlaBreached(true);
            ticketRepository.save(ticket);
            log.warn("SLA breached for ticket #{}: {} (deadline was {})",
                    ticket.getId(), ticket.getTitle(), ticket.getSlaDeadline());
        }

        if (!openTickets.isEmpty()) {
            log.info("SLA check: {} ticket(s) marked as breached", openTickets.size());
        }
    }
}
