package com.financetracker.api.dto.auth;

import com.financetracker.api.dto.Inputs;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Password reset and email verification by emailed 6-digit code. */
public final class RecoveryDtos {

    private RecoveryDtos() {}

    public record ForgotPasswordRequest(
            @NotBlank(message = "Required") @Email(message = "Enter a valid email")
            @Size(max = 255, message = "Enter a valid email") String email) {
        public ForgotPasswordRequest {
            email = Inputs.strip(email);
        }
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Required") @Email(message = "Enter a valid email")
            @Size(max = 255, message = "Enter a valid email") String email,
            @NotBlank(message = "Enter the 6-digit code") @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit code") String code,
            @NotBlank(message = "Required") @Size(min = 10, max = 128, message = "Password must be 10–128 characters") String password) {
        public ResetPasswordRequest {
            email = Inputs.strip(email);
            code = Inputs.strip(code);
        }
    }

    public record VerifyEmailRequest(
            @NotBlank(message = "Enter the 6-digit code") @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit code") String code) {
        public VerifyEmailRequest {
            code = Inputs.strip(code);
        }
    }

    /** {@code sent} is always true for forgot-password: whether the email has an account is not revealed. */
    public record CodeSent(boolean sent) {}

    public record Done(boolean done) {}
}
