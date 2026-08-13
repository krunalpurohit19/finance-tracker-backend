package com.financetracker.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignInRequest {
    @NotBlank(message = "Required")
    @Email(message = "Enter a valid email")
    private String email;

    @NotBlank(message = "Required")
    private String password;
}
