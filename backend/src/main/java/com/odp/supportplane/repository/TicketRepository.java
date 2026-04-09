package com.odp.supportplane.repository;

import com.odp.supportplane.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<Ticket> findByClusterIdOrderByCreatedAtDesc(Long clusterId);
    List<Ticket> findByStatus(String status);
    List<Ticket> findByAssignedToId(Long userId);
    long countByTenantIdAndStatus(Long tenantId, String status);
    void deleteByClusterId(Long clusterId);
}
