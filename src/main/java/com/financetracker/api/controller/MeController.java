package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.settings.SettingsDtos.MeResponse;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.financetracker.api.dto.auth.RecoveryDtos.*;
import com.financetracker.api.service.AccountRecoveryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final SettingsService settingsService;
    private final AccountRecoveryService recovery;

    public MeController(SettingsService settingsService, AccountRecoveryService recovery) {
        this.settingsService = settingsService;
        this.recovery = recovery;
    }

    @GetMapping
    public ApiEnvelope.Success<MeResponse> me() {
        return new ApiEnvelope.Success<>(settingsService.me(SecurityUtils.currentUser()));
    }

    /** Emails a verification code to the signed-in user (no-op once verified). */
    @PostMapping("/verify-email/send")
    public ApiEnvelope.Success<CodeSent> sendVerification() {
        recovery.sendVerification(SecurityUtils.currentUserId());
        return new ApiEnvelope.Success<>(new CodeSent(true));
    }

    @PostMapping("/verify-email")
    public ApiEnvelope.Success<Done> verifyEmail(@Valid @RequestBody VerifyEmailRequest body) {
        recovery.verifyEmail(SecurityUtils.currentUserId(), body.code());
        return new ApiEnvelope.Success<>(new Done(true));
    }
}
