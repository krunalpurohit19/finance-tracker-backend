package com.financetracker.api.service;

import com.financetracker.api.dto.exchangerate.ExchangeRateDtos.UpsertExchangeRateRequest;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    static final String USER = "u1";

    @Mock ExchangeRateRepository rateRepo;
    @Mock UserRepository userRepo;
    @InjectMocks ExchangeRateService service;

    @Test
    void revivesASoftDeletedRateInsteadOfViolatingTheUniqueKey() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        ExchangeRate deleted = ExchangeRate.builder().id("r1").fromCurrency("USD").toCurrency("INR")
                .rate(new BigDecimal("80.00000000")).effectiveFrom(day).deletedAt(Instant.EPOCH).build();
        when(rateRepo.findByUserIdAndFromCurrencyAndToCurrencyAndEffectiveFrom(USER, "USD", "INR", day))
                .thenReturn(Optional.of(deleted));
        when(rateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.upsert(USER, new UpsertExchangeRateRequest("USD", "INR", new BigDecimal("83.25"), day));

        assertThat(result.id()).isEqualTo("r1");
        assertThat(deleted.getDeletedAt()).isNull();
        assertThat(deleted.getRate()).isEqualByComparingTo("83.25").hasToString("83.25000000");
    }

    @Test
    void omittedEffectiveDateAppliesToAllHistory() {
        when(rateRepo.findByUserIdAndFromCurrencyAndToCurrencyAndEffectiveFrom(USER, "USD", "INR", LocalDate.EPOCH))
                .thenReturn(Optional.empty());
        when(rateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.upsert(USER, new UpsertExchangeRateRequest("usd", "inr", BigDecimal.ONE, null));

        assertThat(result.effectiveFrom()).isEqualTo(LocalDate.EPOCH);
        assertThat(result.fromCurrency()).isEqualTo("USD");
        verify(userRepo).getReferenceById(USER);
    }

    @Test
    void sameCurrencyPairIsRejected() {
        assertThatThrownBy(() -> service.upsert(USER, new UpsertExchangeRateRequest("USD", "usd", BigDecimal.ONE, null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
        verify(rateRepo, never()).save(any());
    }
}
