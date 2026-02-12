package com.odp.supportplane.service;

import com.odp.supportplane.model.Tenant;
import com.odp.supportplane.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void generateSlug_lowercasesAndHyphenates() {
        assertEquals("acme-corp", tenantService.generateSlug("Acme Corp"));
    }

    @Test
    void generateSlug_removesSpecialCharacters() {
        assertEquals("my-company-inc", tenantService.generateSlug("My Company, Inc."));
    }

    @Test
    void generateSlug_trimsDashes() {
        assertEquals("hello-world", tenantService.generateSlug("  Hello World  "));
    }

    @Test
    void generateSlug_handlesMultipleSpaces() {
        assertEquals("big-data-co", tenantService.generateSlug("Big   Data   Co"));
    }

    @Test
    void create_savesAndReturnsTenant() {
        Tenant expected = Tenant.builder().id(1L).name("Acme").tenantId("acme").build();
        when(tenantRepository.save(any(Tenant.class))).thenReturn(expected);

        Tenant result = tenantService.create("Acme", "acme");

        assertEquals("Acme", result.getName());
        assertEquals("acme", result.getTenantId());
    }

    @Test
    void existsByTenantId_delegatesToRepository() {
        when(tenantRepository.existsByTenantId("acme")).thenReturn(true);
        when(tenantRepository.existsByTenantId("unknown")).thenReturn(false);

        assertTrue(tenantService.existsByTenantId("acme"));
        assertFalse(tenantService.existsByTenantId("unknown"));
    }

    @Test
    void findByTenantId_returnsOptional() {
        Tenant tenant = Tenant.builder().id(1L).name("Acme").tenantId("acme").build();
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.of(tenant));
        when(tenantRepository.findByTenantId("nope")).thenReturn(Optional.empty());

        assertTrue(tenantService.findByTenantId("acme").isPresent());
        assertFalse(tenantService.findByTenantId("nope").isPresent());
    }
}
