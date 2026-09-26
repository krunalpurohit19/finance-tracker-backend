package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.User;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.ThemePreference;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.UserRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final UserSettingsRepository settingsRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public SettingsController(UserSettingsRepository settingsRepo, UserRepository userRepo,
                               PasswordEncoder passwordEncoder) {
        this.settingsRepo = settingsRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> get() {
        String userId = SecurityUtils.currentUserId();
        User user = SecurityUtils.currentUser();
        UserSettings s = settingsRepo.findById(userId).orElseThrow();

        Map<String, Object> data = new LinkedHashMap<>();
        
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("createdAt", user.getCreatedAt().toString());
        data.put("profile", profile);

        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("baseCurrency", s.getBaseCurrency());
        prefs.put("locale", s.getLocale());
        prefs.put("timezone", s.getTimezone());
        prefs.put("dateFormat", s.getDateFormat());
        prefs.put("theme", s.getTheme().name());
        prefs.put("weekStartsOn", s.getWeekStartsOn());
        data.put("preferences", prefs);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("accounts", userRepo.countAccounts(userId));
        counts.put("categories", userRepo.countCategories(userId));
        counts.put("transactions", userRepo.countTransactions(userId));
        counts.put("budgets", userRepo.countBudgets(userId));
        counts.put("goals", userRepo.countGoals(userId));
        counts.put("recurring", userRepo.countRecurring(userId));
        counts.put("exchangeRates", userRepo.countExchangeRates(userId));
        data.put("counts", counts);

        return ResponseEntity.ok(new ApiEnvelope.Success<>(data));
    }

    @PatchMapping("/profile")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> updateProfile(@RequestBody Map<String, Object> body) {
        User user = SecurityUtils.currentUser();
        if (body.containsKey("name")) user.setName((String) body.get("name"));
        userRepo.save(user);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("name", user.getName())));
    }

    @PatchMapping("/preferences")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> updatePreferences(@RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.currentUserId();
        UserSettings s = settingsRepo.findById(userId).orElseThrow();
        if (body.containsKey("locale")) s.setLocale((String) body.get("locale"));
        if (body.containsKey("timezone")) s.setTimezone((String) body.get("timezone"));
        if (body.containsKey("dateFormat")) s.setDateFormat((String) body.get("dateFormat"));
        if (body.containsKey("theme")) s.setTheme(ThemePreference.valueOf((String) body.get("theme")));
        if (body.containsKey("weekStartsOn")) s.setWeekStartsOn((int) body.get("weekStartsOn"));
        settingsRepo.save(s);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("updated", true)));
    }

    @PostMapping("/base-currency")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> changeBaseCurrency(@RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.currentUserId();
        UserSettings s = settingsRepo.findById(userId).orElseThrow();
        String oldCurrency = s.getBaseCurrency();
        String newCurrency = ((String) body.get("currency")).toUpperCase();
        s.setBaseCurrency(newCurrency);
        settingsRepo.save(s);
        // Note: mass rewrite of baseAmount on transactions would go here in production
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("from", oldCurrency, "to", newCurrency, "repriced", 0)));
    }

    @PostMapping("/delete-account")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> deleteAccount(@RequestBody Map<String, Object> body) {
        User user = SecurityUtils.currentUser();
        String password = (String) body.get("password");
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw ApiException.unauthenticated("Incorrect password");
        }
        user.setDeletedAt(Instant.now());
        userRepo.save(user);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("deleted", true)));
    }
}
