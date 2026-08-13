package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.auth.*;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sign-up")
    public ResponseEntity<ApiEnvelope.Success<AuthResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        AuthResponse response = authService.signUp(request);
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
