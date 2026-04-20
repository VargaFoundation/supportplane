package varga.supportplane.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String companyName;

    @NotBlank @Email
    private String email;

    @NotBlank
    private String fullName;

    @NotBlank @Size(min = 8)
    private String password;
}
