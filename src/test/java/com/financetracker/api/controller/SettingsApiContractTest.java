package com.financetracker.api.controller;

import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.User;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.ThemePreference;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.RefreshTokenRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.service.ExchangeRateService;
import com.financetracker.api.service.SettingsService;
import com.financetracker.api.support.WebSliceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the JSON contract of /api/v1/settings, /api/v1/me and /api/v1/exchange-rates:
 * every response body is compared STRICTLY (no missing, extra or re-typed keys), so a
 * refactor that changes what the mobile client receives fails here.
 */
@WebMvcTest({SettingsController.class, MeController.class, ExchangeRateController.class})
@Import({SettingsService.class, ExchangeRateService.class})
class SettingsApiContractTest extends WebSliceTest {

    @MockitoBean UserSettingsRepository settingsRepo;
    @MockitoBean ExchangeRateRepository rateRepo;
    @MockitoBean RefreshTokenRepository refreshTokenRepo;
    @Autowired PasswordEncoder passwordEncoder;

    UserSettings settings;

    @BeforeEach
    void setUp() {
        settings = UserSettings.builder().userId(USER_ID).build(); // INR, en-IN, Asia/Kolkata, dd/MM/yyyy, SYSTEM, 1
        when(settingsRepo.findById(USER_ID)).thenReturn(Optional.of(settings));
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(testUser()));
        when(rateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ResultActions send(MockHttpServletRequestBuilder req, String json) throws Exception {
        return mvc.perform(req.with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static void expectBody(ResultActions r, String json) throws Exception {
        r.andExpect(status().isOk()).andExpect(content().json(json, JsonCompareMode.STRICT));
    }

    // ── GET /api/v1/settings ────────────────────────────────────────────

    @Test
    void settingsReturnsProfilePreferencesAndCounts() throws Exception {
        when(userRepository.countAccounts(USER_ID)).thenReturn(1L);
        when(userRepository.countCategories(USER_ID)).thenReturn(2L);
        when(userRepository.countTransactions(USER_ID)).thenReturn(3L);
        when(userRepository.countBudgets(USER_ID)).thenReturn(4L);
        when(userRepository.countGoals(USER_ID)).thenReturn(5L);
        when(userRepository.countRecurring(USER_ID)).thenReturn(6L);
        when(userRepository.countExchangeRates(USER_ID)).thenReturn(7L);

        expectBody(mvc.perform(get("/api/v1/settings").with(asUser())), """
                {"ok":true,"data":{
                  "profile":{"id":"%s","name":"Test User","email":"test@example.test","createdAt":"1970-01-01T00:00:00Z"},
                  "preferences":{"baseCurrency":"INR","locale":"en-IN","timezone":"Asia/Kolkata",
                                 "dateFormat":"dd/MM/yyyy","theme":"SYSTEM","weekStartsOn":1},
                  "counts":{"accounts":1,"categories":2,"transactions":3,"budgets":4,"goals":5,
                            "recurring":6,"exchangeRates":7}}}""".formatted(USER_ID));
    }

    @Test
    void settingsOmitsNullName() throws Exception {
        User nameless = testUser();
        nameless.setName(null);
        mvc.perform(get("/api/v1/settings").with(asUser(nameless)))
           .andExpect(status().isOk())
           .andExpect(content().json("""
                   {"ok":true,"data":{"profile":{"id":"%s","email":"test@example.test","createdAt":"1970-01-01T00:00:00Z"}}}"""
                   .formatted(USER_ID), JsonCompareMode.LENIENT))
           .andExpect(r -> assertThat(r.getResponse().getContentAsString()).doesNotContain("\"name\""));
    }

    // ── GET /api/v1/me ──────────────────────────────────────────────────

    @Test
    void meReturnsUserAndSettings() throws Exception {
        expectBody(mvc.perform(get("/api/v1/me").with(asUser())), """
                {"ok":true,"data":{
                  "user":{"id":"%s","name":"Test User","email":"test@example.test"},
                  "settings":{"baseCurrency":"INR","locale":"en-IN","timezone":"Asia/Kolkata",
                              "dateFormat":"dd/MM/yyyy","theme":"SYSTEM","weekStartsOn":1}}}""".formatted(USER_ID));
    }

    @Test
    void meOmitsSettingsWhenMissing() throws Exception {
        when(settingsRepo.findById(USER_ID)).thenReturn(Optional.empty());
        expectBody(mvc.perform(get("/api/v1/me").with(asUser())), """
                {"ok":true,"data":{"user":{"id":"%s","name":"Test User","email":"test@example.test"}}}""".formatted(USER_ID));
    }

    // ── PATCH profile / preferences ─────────────────────────────────────

    @Test
    void updateProfileReturnsName() throws Exception {
        expectBody(send(patch("/api/v1/settings/profile"), """
                {"name":"Asha Rao"}"""), """
                {"ok":true,"data":{"name":"Asha Rao"}}""");
    }

    @Test
    void updatePreferencesChangesOnlySentFields() throws Exception {
        expectBody(send(patch("/api/v1/settings/preferences"), """
                {"theme":"DARK","weekStartsOn":0}"""), """
                {"ok":true,"data":{"updated":true}}""");
        assertThat(settings.getTheme()).isEqualTo(ThemePreference.DARK);
        assertThat(settings.getWeekStartsOn()).isZero();
        assertThat(settings.getLocale()).isEqualTo("en-IN");
        assertThat(settings.getTimezone()).isEqualTo("Asia/Kolkata");
    }

    // ── POST base-currency ──────────────────────────────────────────────

    @Test
    void changeBaseCurrencyReportsFromAndTo() throws Exception {
        expectBody(send(post("/api/v1/settings/base-currency"), """
                {"currency":"usd"}"""), """
                {"ok":true,"data":{"from":"INR","to":"USD","repriced":0}}""");
        assertThat(settings.getBaseCurrency()).isEqualTo("USD");
    }

    // ── POST delete-account ─────────────────────────────────────────────

    @Test
    void deleteAccountWithCorrectPasswordSoftDeletes() throws Exception {
        User user = testUser();
        user.setPassword(passwordEncoder.encode("correct horse"));
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));

        mvc.perform(post("/api/v1/settings/delete-account").with(asUser(user))
                   .contentType(MediaType.APPLICATION_JSON)
                   .content("{\"password\":\"correct horse\",\"confirm\":\"DELETE\"}"))
           .andExpect(status().isOk())
           .andExpect(content().json("{\"ok\":true,\"data\":{\"deleted\":true}}", JsonCompareMode.STRICT));
        assertThat(user.getDeletedAt()).isNotNull();
    }

    // ── /api/v1/exchange-rates ──────────────────────────────────────────

    private static ExchangeRate rate(String id, String rate) {
        return ExchangeRate.builder().id(id).fromCurrency("USD").toCurrency("INR")
                .rate(new BigDecimal(rate)).effectiveFrom(LocalDate.of(2026, 1, 1)).build();
    }

    @Test
    void listRates() throws Exception {
        when(rateRepo.findActiveByUserId(USER_ID)).thenReturn(List.of(rate("r1", "83.25000000"), rate("r2", "0.00000001")));
        expectBody(mvc.perform(get("/api/v1/exchange-rates").with(asUser())), """
                {"ok":true,"data":[{"id":"r1","fromCurrency":"USD","toCurrency":"INR",
                                    "rate":"83.25000000","effectiveFrom":"2026-01-01"},
                                   {"id":"r2","fromCurrency":"USD","toCurrency":"INR",
                                    "rate":"0.00000001","effectiveFrom":"2026-01-01"}]}""");
    }

    @Test
    void upsertCreatesNewRate() throws Exception {
        var r = send(put("/api/v1/exchange-rates"), """
                {"fromCurrency":"usd","toCurrency":"inr","rate":"83.25","effectiveFrom":"2026-01-01"}""");
        var saved = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(rateRepo).save(saved.capture());
        expectBody(r, """
                {"ok":true,"data":{"id":"%s","fromCurrency":"USD","toCurrency":"INR",
                                   "rate":"83.25","effectiveFrom":"2026-01-01"}}""".formatted(saved.getValue().getId()));
    }

    @Test
    void upsertUpdatesExistingRate() throws Exception {
        ExchangeRate existing = rate("r1", "80.00000000");
        when(rateRepo.findByUserIdAndFromCurrencyAndToCurrencyAndEffectiveFromAndDeletedAtIsNull(
                USER_ID, "USD", "INR", LocalDate.of(2026, 1, 1))).thenReturn(Optional.of(existing));

        expectBody(send(put("/api/v1/exchange-rates"), """
                {"fromCurrency":"USD","toCurrency":"INR","rate":"83.25","effectiveFrom":"2026-01-01"}"""), """
                {"ok":true,"data":{"id":"r1","fromCurrency":"USD","toCurrency":"INR",
                                   "rate":"83.25","effectiveFrom":"2026-01-01"}}""");
    }

    @Test
    void deleteRateSoftDeletes() throws Exception {
        ExchangeRate existing = rate("r1", "80.00000000");
        when(rateRepo.findByIdAndUserIdAndDeletedAtIsNull("r1", USER_ID)).thenReturn(Optional.of(existing));

        expectBody(mvc.perform(delete("/api/v1/exchange-rates/r1").with(asUser())), """
                {"ok":true,"data":{"id":"r1"}}""");
        assertThat(existing.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteUnknownRateIs404() throws Exception {
        mvc.perform(delete("/api/v1/exchange-rates/nope").with(asUser()))
           .andExpect(status().isNotFound())
           .andExpect(content().json("""
                   {"ok":false,"error":{"code":"NOT_FOUND","message":"Exchange rate not found"}}""", JsonCompareMode.STRICT));
    }

    // ── Input validation: 422 VALIDATION_FAILED with field errors (was 500) ──

    private void expectFieldError(ResultActions r, String field, String message) throws Exception {
        r.andExpect(status().is(422))
         .andExpect(jsonPath("$.ok").value(false))
         .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
         .andExpect(jsonPath("$.error.fieldErrors['" + field + "'][0]").value(message));
    }

    @Test
    void profileNameIsTrimmedAndRequired() throws Exception {
        expectBody(send(patch("/api/v1/settings/profile"), "{\"name\":\"  Asha  \"}"),
                "{\"ok\":true,\"data\":{\"name\":\"Asha\"}}");
        expectFieldError(send(patch("/api/v1/settings/profile"), "{\"name\":\"   \"}"), "name", "Required");
        expectFieldError(send(patch("/api/v1/settings/profile"), "{}"), "name", "Required");
        expectFieldError(send(patch("/api/v1/settings/profile"), "{\"name\":\"" + "x".repeat(61) + "\"}"),
                "name", "Keep this under 60 characters");
    }

    @Test
    void preferencesRejectValuesOutsideTheMobileLists() throws Exception {
        expectFieldError(send(patch("/api/v1/settings/preferences"), "{\"locale\":\"fr-FR\"}"), "locale", "Choose a supported locale");
        expectFieldError(send(patch("/api/v1/settings/preferences"), "{\"dateFormat\":\"yyyy\"}"), "dateFormat", "Choose a supported date format");
        expectFieldError(send(patch("/api/v1/settings/preferences"), "{\"theme\":\"PURPLE\"}"), "theme", "Invalid value");
        expectFieldError(send(patch("/api/v1/settings/preferences"), "{\"weekStartsOn\":7}"), "weekStartsOn", "Choose a day of the week");
        expectFieldError(send(patch("/api/v1/settings/preferences"), "{\"weekStartsOn\":\"monday\"}"), "weekStartsOn", "Invalid value");
        for (String tz : List.of("IST", "EST", "+05:30", "Mars/Olympus")) {
            expectFieldError(send(patch("/api/v1/settings/preferences"), "{\"timezone\":\"" + tz + "\"}"),
                    "timezone", "Enter an IANA timezone such as Asia/Kolkata");
        }
        assertThat(settings.getLocale()).isEqualTo("en-IN");
        assertThat(settings.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(settings.getTheme()).isEqualTo(ThemePreference.SYSTEM);
    }

    @Test
    void preferencesAcceptEveryMobileOption() throws Exception {
        for (String body : List.of("{\"timezone\":\"UTC\"}", "{\"timezone\":\"America/New_York\"}",
                "{\"locale\":\"en-SG\"}", "{\"dateFormat\":\"d MMM yyyy\"}", "{\"weekStartsOn\":6}")) {
            send(patch("/api/v1/settings/preferences"), body).andExpect(status().isOk());
        }
    }

    @Test
    void baseCurrencyMustBeACurrencyCode() throws Exception {
        expectFieldError(send(post("/api/v1/settings/base-currency"), "{}"), "currency", "Required");
        expectFieldError(send(post("/api/v1/settings/base-currency"), "{\"currency\":\"dollars\"}"),
                "currency", "Enter a 3-letter currency code");
        assertThat(settings.getBaseCurrency()).isEqualTo("INR");
    }

    @Test
    void deleteAccountRequiresPassword() throws Exception {
        expectFieldError(send(post("/api/v1/settings/delete-account"), "{\"confirm\":\"DELETE\"}"),
                "password", "Enter your password");
    }

    @Test
    void upsertRateValidatesEveryField() throws Exception {
        var r = send(put("/api/v1/exchange-rates"), "{}");
        expectFieldError(r, "fromCurrency", "Required");
        r.andExpect(jsonPath("$.error.fieldErrors.toCurrency[0]").value("Required"))
         .andExpect(jsonPath("$.error.fieldErrors.rate[0]").value("Required"))
         .andExpect(jsonPath("$.error.fieldErrors.effectiveFrom[0]").value("Required"));

        String base = "{\"fromCurrency\":\"USD\",\"toCurrency\":\"INR\",\"effectiveFrom\":\"2026-01-01\",";
        expectFieldError(send(put("/api/v1/exchange-rates"), base + "\"rate\":\"0\"}"), "rate", "Rate must be greater than zero");
        expectFieldError(send(put("/api/v1/exchange-rates"), base + "\"rate\":\"-1\"}"), "rate", "Rate must be greater than zero");
        expectFieldError(send(put("/api/v1/exchange-rates"), base + "\"rate\":\"1.123456789\"}"), "rate", "Enter a valid rate");
        expectFieldError(send(put("/api/v1/exchange-rates"), base + "\"rate\":\"abc\"}"), "rate", "Invalid value");
        expectFieldError(send(put("/api/v1/exchange-rates"),
                "{\"fromCurrency\":\"US\",\"toCurrency\":\"INR\",\"rate\":\"1\",\"effectiveFrom\":\"2026-01-01\"}"),
                "fromCurrency", "Enter a 3-letter currency code");
        verify(rateRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void upsertAcceptsJsonNumberRateExactly() throws Exception {
        send(put("/api/v1/exchange-rates"), """
                {"fromCurrency":"USD","toCurrency":"INR","rate":83.12345678,"effectiveFrom":"2026-01-01"}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rate").value("83.12345678"));
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        for (var req : List.of(get("/api/v1/settings"), get("/api/v1/me"), get("/api/v1/exchange-rates"))) {
            mvc.perform(req).andExpect(status().isUnauthorized());
        }
    }
}
