package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.User;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserSettingsRepository settingsRepo;

    public MeController(UserSettingsRepository settingsRepo) {
        this.settingsRepo = settingsRepo;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> me() {
        User user = SecurityUtils.currentUser();
        UserSettings settings = settingsRepo.findById(user.getId()).orElse(null);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("name", user.getName());
        userData.put("email", user.getEmail());

        Map<String, Object> data = new HashMap<>();
        data.put("user", userData);
        if (settings != null) {
            Map<String, Object> s = new HashMap<>();
            s.put("baseCurrency", settings.getBaseCurrency());
            s.put("locale", settings.getLocale());
            s.put("timezone", settings.getTimezone());
            s.put("dateFormat", settings.getDateFormat());
            s.put("theme", settings.getTheme().name());
            s.put("weekStartsOn", settings.getWeekStartsOn());
            data.put("settings", s);
        }

        return ResponseEntity.ok(new ApiEnvelope.Success<>(data));
    }
}
