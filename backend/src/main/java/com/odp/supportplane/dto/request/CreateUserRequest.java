package com.odp.supportplane.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String fullName;

    @NotBlank
    private String password;

    private String role;

    // Optional: operator can specify target tenant (slug)
    private String tenantId;
}
