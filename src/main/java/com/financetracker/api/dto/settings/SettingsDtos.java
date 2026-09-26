package com.financetracker.api.dto.settings;

import com.financetracker.api.dto.Inputs;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.ThemePreference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request/response shapes for /api/v1/settings and /api/v1/me. Rules mirror mobile src/validation/settings.ts. */
public final class SettingsDtos {

    private SettingsDtos() {}

    /** Closed lists offered by the mobile Settings screens (LOCALES, DATE_FORMATS). */
    static final String LOCALES = "en-IN|en-US|en-GB|en-AU|en-CA|en-SG";
    static final String DATE_FORMATS = "dd/MM/yyyy|MM/dd/yyyy|yyyy-MM-dd|d MMM yyyy";

    // ── Requests ─────────────────────────────────────────────────────────

    public record UpdateProfileRequest(
            @NotBlank(message = "Required") @Size(max = 60, message = "Keep this under 60 characters") String name) {
        public UpdateProfileRequest { name = Inputs.strip(name); }
    }

    /** PATCH: a field left out (null) is left unchanged. The timezone's existence is checked by the service. */
    public record UpdatePreferencesRequest(
            @Pattern(regexp = LOCALES, message = "Choose a supported locale") String locale,
            @Size(max = 60, message = "Enter an IANA timezone such as Asia/Kolkata") String timezone,
            @Pattern(regexp = DATE_FORMATS, message = "Choose a supported date format") String dateFormat,
            ThemePreference theme,
            @Min(value = 0, message = "Choose a day of the week") @Max(value = 6, message = "Choose a day of the week")
            Integer weekStartsOn) {
        public UpdatePreferencesRequest { timezone = Inputs.strip(timezone); }
    }

    public record ChangeBaseCurrencyRequest(
            @NotNull(message = "Required") @Pattern(regexp = "[A-Z]{3}", message = "Enter a 3-letter currency code")
            String currency) {
        public ChangeBaseCurrencyRequest { currency = Inputs.currency(currency); }
    }

    public record DeleteAccountRequest(@NotBlank(message = "Enter your password") String password, String confirm) {}

    // ── Responses ────────────────────────────────────────────────────────

    public record SettingsResponse(Profile profile, Preferences preferences, Counts counts) {}

    public record Profile(String id, String name, String email, Instant createdAt) {}

    public record Preferences(String baseCurrency, String locale, String timezone, String dateFormat,
                              ThemePreference theme, int weekStartsOn) {
        public static Preferences of(UserSettings s) {
            return new Preferences(s.getBaseCurrency(), s.getLocale(), s.getTimezone(), s.getDateFormat(),
                    s.getTheme(), s.getWeekStartsOn());
        }
    }

    public record Counts(long accounts, long categories, long transactions, long budgets, long goals,
                         long recurring, long exchangeRates) {}

    /** {@code settings} is omitted when the user has no settings row. */
    public record MeResponse(MeUser user, Preferences settings) {}

    public record MeUser(String id, String name, String email) {}

    public record ProfileUpdated(String name) {}

    public record PreferencesUpdated(boolean updated) {}

    public record BaseCurrencyChanged(String from, String to, int repriced) {}

    public record AccountDeleted(boolean deleted) {}
}
