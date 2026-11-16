package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.auth.*;
import com.financetracker.api.dto.auth.RecoveryDtos.*;
import com.financetracker.api.service.AccountRecoveryService;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountRecoveryService recovery;

    public AuthController(AuthService authService, AccountRecoveryService recovery) {
        this.authService = authService;
        this.recovery = recovery;
    }

    /** Emails a 6-digit code if the address has an account; the answer is the same either way. */
    @PostMapping("/forgot-password")
    public ApiEnvelope.Success<CodeSent> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        recovery.requestPasswordReset(request.email());
        return new ApiEnvelope.Success<>(new CodeSent(true));
    }

    /** Sets a new password with the emailed code and signs every device out. */
    @PostMapping("/reset-password")
    public ApiEnvelope.Success<Done> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recovery.resetPassword(request);
        return new ApiEnvelope.Success<>(new Done(true));
    }

    @PostMapping("/sign-up")
    public ResponseEntity<ApiEnvelope.Success<AuthResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        AuthResponse response = authService.signUp(request);
        recovery.sendVerification(response.getUser().getId()); // best effort; never fails sign-up
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(response));
    }

    @PostMapping("/sign-in")
    public ResponseEntity<ApiEnvelope.Success<AuthResponse>> signIn(@Valid @RequestBody SignInRequest request) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(authService.signIn(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiEnvelope.Success<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(authService.refresh(request)));
    }

    @PostMapping("/sign-out")
    public ResponseEntity<ApiEnvelope.Success<Object>> signOut() {
        authService.signOut(SecurityUtils.currentUserId());
        return ResponseEntity.ok(new ApiEnvelope.Success<>(null));
    }
}
