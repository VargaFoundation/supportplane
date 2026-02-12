package com.odp.supportplane.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odp.supportplane.TestHelper;
import com.odp.supportplane.config.SecurityConfig;
import com.odp.supportplane.config.TenantFilter;
import com.odp.supportplane.dto.request.CreateTicketRequest;
import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.model.Ticket;
import com.odp.supportplane.model.TicketComment;
import com.odp.supportplane.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, TenantFilter.class})
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private Ticket testTicket() {
        Tenant tenant = Tenant.builder().id(1L).name("Acme").tenantId("acme").build();
        return Ticket.builder()
                .id(1L).tenant(tenant).title("HDFS issue")
                .description("DataNodes down").status("OPEN").priority("HIGH")
                .build();
    }

    @Test
    void listTickets_success() throws Exception {
        when(ticketService.getTickets()).thenReturn(List.of(testTicket()));

        mockMvc.perform(get("/api/v1/tickets")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("HDFS issue"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void getTicket_found() throws Exception {
        when(ticketService.findById(1L)).thenReturn(Optional.of(testTicket()));

        mockMvc.perform(get("/api/v1/tickets/1")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("HDFS issue"));
    }

    @Test
    void getTicket_notFound() throws Exception {
        when(ticketService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tickets/999")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTicket_success() throws Exception {
        when(ticketService.create(anyString(), anyString(), isNull(), anyString(), isNull()))
                .thenReturn(testTicket());

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("HDFS issue");
        request.setDescription("DataNodes down");
        request.setPriority("HIGH");

        mockMvc.perform(post("/api/v1/tickets")
                        .with(TestHelper.adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("HDFS issue"));
    }

    @Test
    void createTicket_missingTitle_returns400() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setDescription("Some description");

        mockMvc.perform(post("/api/v1/tickets")
                        .with(TestHelper.adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_success() throws Exception {
        Ticket updated = testTicket();
        updated.setStatus("IN_PROGRESS");
        when(ticketService.updateStatus(1L, "IN_PROGRESS")).thenReturn(updated);

        mockMvc.perform(put("/api/v1/tickets/1/status")
                        .with(TestHelper.adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void assignTicket_success() throws Exception {
        Ticket assigned = testTicket();
        assigned.setStatus("ASSIGNED");
        when(ticketService.assign(1L, 2L)).thenReturn(assigned);

        mockMvc.perform(put("/api/v1/tickets/1/assign")
                        .with(TestHelper.adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void addComment_success() throws Exception {
        TicketComment comment = TicketComment.builder()
                .id(1L).content("Working on it").build();
        when(ticketService.addComment(eq(1L), isNull(), eq("Working on it"))).thenReturn(comment);

        mockMvc.perform(post("/api/v1/tickets/1/comments")
                        .with(TestHelper.adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Working on it"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Working on it"));
    }

    @Test
    void getComments_success() throws Exception {
        when(ticketService.getComments(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tickets/1/comments")
                        .with(TestHelper.adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
