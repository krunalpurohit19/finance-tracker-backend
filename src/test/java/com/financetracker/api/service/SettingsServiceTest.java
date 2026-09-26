package com.financetracker.api.service;

import com.financetracker.api.dto.settings.SettingsDtos.ChangeBaseCurrencyRequest;
import com.financetracker.api.dto.settings.SettingsDtos.DeleteAccountRequest;
import com.financetracker.api.dto.settings.SettingsDtos.UpdateProfileRequest;
import com.financetracker.api.entity.User;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.RefreshTokenRepository;
import com.financetracker.api.repository.UserRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    static final String USER = "u1";

    @Mock UserSettingsRepository settingsRepo;
    @Mock UserRepository userRepo;
    @Mock RefreshTokenRepository refreshTokenRepo;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks SettingsService service;

    UserSettings settings;

    @BeforeEach
    void setUp() {
        settings = UserSettings.builder().userId(USER).build();
        lenient().when(settingsRepo.findById(USER)).thenReturn(Optional.of(settings));
    }

    // ── base currency guard ──

    @Test
    void switchesBaseCurrencyWhenNothingIsDenominatedInIt() {
        var result = service.changeBaseCurrency(USER, new ChangeBaseCurrencyRequest("usd", null));
        assertThat(result.from()).isEqualTo("INR");
        assertThat(result.to()).isEqualTo("USD");
        assertThat(settings.getBaseCurrency()).isEqualTo("USD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"transactions", "budgets", "goals"})
    void refusesToRelabelExistingMoney(String what) {
        switch (what) {
            case "transactions" -> when(userRepo.countTransactions(USER)).thenReturn(1L);
            case "budgets" -> when(userRepo.countBudgets(USER)).thenReturn(1L);
            default -> when(userRepo.countGoals(USER)).thenReturn(1L);
        }
        assertThatThrownBy(() -> service.changeBaseCurrency(USER, new ChangeBaseCurrencyRequest("USD", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
        assertThat(settings.getBaseCurrency()).isEqualTo("INR");
    }

    @Test
    void sameCurrencyIsANoOpEvenWithHistory() {
        lenient().when(userRepo.countTransactions(USER)).thenReturn(5L);
        var result = service.changeBaseCurrency(USER, new ChangeBaseCurrencyRequest("INR", null));
        assertThat(result.from()).isEqualTo("INR");
        assertThat(result.to()).isEqualTo("INR");
        verify(userRepo, never()).countTransactions(USER);
    }

    // ── delete account ──

    @Test
    void deleteAccountSoftDeletesAndRevokesRefreshTokens() {
        User user = User.builder().id(USER).password("hash").build();
        when(userRepo.findByIdAndDeletedAtIsNull(USER)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        service.deleteAccount(USER, new DeleteAccountRequest("pw", "DELETE"));

        assertThat(user.getDeletedAt()).isNotNull();
        verify(refreshTokenRepo).deleteAllByUserId(USER);
        verify(userRepo).findByIdAndDeletedAtIsNull(USER);
        verifyNoMoreInteractions(userRepo, refreshTokenRepo); // soft delete only: no delete*/deleteById
    }

    @Test
    void deleteAccountForMissingUserIsNotFoundAndRevokesNothing() {
        when(userRepo.findByIdAndDeletedAtIsNull(USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteAccount(USER, new DeleteAccountRequest("pw", "DELETE")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
        verifyNoInteractions(refreshTokenRepo);
    }

    // ── profile ──

    @Test
    void updateProfileRenamesTheManagedUserNotThePrincipal() {
        User managed = User.builder().id(USER).name("Old").build();
        when(userRepo.findByIdAndDeletedAtIsNull(USER)).thenReturn(Optional.of(managed));

        var result = service.updateProfile(USER, new UpdateProfileRequest("Asha"));

        assertThat(managed.getName()).isEqualTo("Asha"); // no security context here: SecurityUtils would throw
        assertThat(result.name()).isEqualTo("Asha");
    }

    @Test
    void wrongPasswordChangesNothing() {
        User user = User.builder().id(USER).password("hash").build();
        when(userRepo.findByIdAndDeletedAtIsNull(USER)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.deleteAccount(USER, new DeleteAccountRequest("nope", "DELETE")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
        assertThat(user.getDeletedAt()).isNull();
        verify(refreshTokenRepo, never()).deleteAllByUserId(USER);
    }

    // ── timezone rule (mirrors mobile primitives.ts) ──

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Asia/Kolkata", "Asia/Calcutta", "America/Argentina/Buenos_Aires"})
    void acceptsIanaZones(String tz) {
        assertThat(SettingsService.isIanaZone(tz)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"IST", "EST", "Z", "+05:30", "GMT+5", "Mars/Olympus", ""})
    void rejectsAbbreviationsOffsetsAndUnknownZones(String tz) {
        assertThat(SettingsService.isIanaZone(tz)).isFalse();
    }
}
