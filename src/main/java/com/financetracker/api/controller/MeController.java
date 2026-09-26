package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.settings.SettingsDtos.MeResponse;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final SettingsService settingsService;

    public MeController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiEnvelope.Success<MeResponse> me() {
        return new ApiEnvelope.Success<>(settingsService.me(SecurityUtils.currentUser()));
    }
}
