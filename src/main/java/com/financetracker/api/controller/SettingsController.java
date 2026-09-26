package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.settings.SettingsDtos.*;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiEnvelope.Success<SettingsResponse> get() {
        return new ApiEnvelope.Success<>(settingsService.get(SecurityUtils.currentUser()));
    }

    @PatchMapping("/profile")
    public ApiEnvelope.Success<ProfileUpdated> updateProfile(@Valid @RequestBody UpdateProfileRequest body) {
        return new ApiEnvelope.Success<>(settingsService.updateProfile(SecurityUtils.currentUserId(), body));
    }

    @PatchMapping("/preferences")
    public ApiEnvelope.Success<PreferencesUpdated> updatePreferences(@Valid @RequestBody UpdatePreferencesRequest body) {
        return new ApiEnvelope.Success<>(settingsService.updatePreferences(SecurityUtils.currentUserId(), body));
    }

    @PostMapping("/base-currency")
    public ApiEnvelope.Success<BaseCurrencyChanged> changeBaseCurrency(@Valid @RequestBody ChangeBaseCurrencyRequest body) {
        return new ApiEnvelope.Success<>(settingsService.changeBaseCurrency(SecurityUtils.currentUserId(), body));
    }

    @PostMapping("/delete-account")
    public ApiEnvelope.Success<AccountDeleted> deleteAccount(@Valid @RequestBody DeleteAccountRequest body) {
        return new ApiEnvelope.Success<>(settingsService.deleteAccount(SecurityUtils.currentUserId(), body));
    }
}
