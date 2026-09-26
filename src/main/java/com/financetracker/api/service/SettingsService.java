package com.financetracker.api.service;

import com.financetracker.api.dto.settings.SettingsDtos.*;
import com.financetracker.api.entity.User;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.UserRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class SettingsService {

    private final UserSettingsRepository settingsRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public SettingsService(UserSettingsRepository settingsRepo, UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.settingsRepo = settingsRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /** @param user the authenticated principal (read-only use) */
    @Transactional(readOnly = true)
    public SettingsResponse get(User user) {
        String userId = user.getId();
        var counts = new Counts(
                userRepo.countAccounts(userId), userRepo.countCategories(userId), userRepo.countTransactions(userId),
                userRepo.countBudgets(userId), userRepo.countGoals(userId), userRepo.countRecurring(userId),
                userRepo.countExchangeRates(userId));
        return new SettingsResponse(
                new Profile(userId, user.getName(), user.getEmail(), user.getCreatedAt()),
                Preferences.of(settings(userId)),
                counts);
    }

    /** @param user the authenticated principal (read-only use) */
    @Transactional(readOnly = true)
    public MeResponse me(User user) {
        return new MeResponse(new MeUser(user.getId(), user.getName(), user.getEmail()),
                settingsRepo.findById(user.getId()).map(Preferences::of).orElse(null));
    }

    @Transactional
    public ProfileUpdated updateProfile(String userId, UpdateProfileRequest req) {
        User user = activeUser(userId);
        user.setName(req.name());
        return new ProfileUpdated(user.getName());
    }

    @Transactional
    public PreferencesUpdated updatePreferences(String userId, UpdatePreferencesRequest req) {
        if (req.timezone() != null && !isIanaZone(req.timezone())) {
            throw ApiException.validationFailed("Some of the details you entered need fixing",
                    Map.of("timezone", List.of("Enter an IANA timezone such as Asia/Kolkata")));
        }
        UserSettings s = settings(userId);
        if (req.locale() != null) s.setLocale(req.locale());
        if (req.timezone() != null) s.setTimezone(req.timezone());
        if (req.dateFormat() != null) s.setDateFormat(req.dateFormat());
        if (req.theme() != null) s.setTheme(req.theme());
        if (req.weekStartsOn() != null) s.setWeekStartsOn(req.weekStartsOn());
        return new PreferencesUpdated(true);
    }

    @Transactional
    public BaseCurrencyChanged changeBaseCurrency(String userId, ChangeBaseCurrencyRequest req) {
        UserSettings s = settings(userId);
        String from = s.getBaseCurrency();
        s.setBaseCurrency(req.currency());
        // Note: mass rewrite of baseAmount on transactions would go here in production
        return new BaseCurrencyChanged(from, req.currency(), 0);
    }

    @Transactional
    public AccountDeleted deleteAccount(String userId, DeleteAccountRequest req) {
        User user = activeUser(userId);
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw ApiException.unauthenticated("Incorrect password");
        }
        user.setDeletedAt(Instant.now());
        return new AccountDeleted(true);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private UserSettings settings(String userId) {
        return settingsRepo.findById(userId).orElseThrow(() -> ApiException.notFound("Settings not found"));
    }

    /** Managed copy: never mutate the detached principal from the security context. */
    private User activeUser(String userId) {
        return userRepo.findByIdAndDeletedAtIsNull(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    /** Mirrors the mobile rule: Area/Location (or UTC), and known to the tz database. */
    static boolean isIanaZone(String tz) {
        return (tz.equals("UTC") || tz.contains("/")) && ZoneId.getAvailableZoneIds().contains(tz);
    }
}
