package com.financetracker.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpRequest {
    @NotBlank(message = "Required")
    private String name;

    @NotBlank(message = "Required")
    @Email(message = "Enter a valid email")
    private String email;

    @NotBlank(message = "Required")
    @Size(min = 10, max = 128, message = "Password must be 10–128 characters")
    private String password;
}
