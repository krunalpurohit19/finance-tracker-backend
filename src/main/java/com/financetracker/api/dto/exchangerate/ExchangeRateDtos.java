package com.financetracker.api.dto.exchangerate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.financetracker.api.dto.Inputs;
import com.financetracker.api.entity.ExchangeRate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request/response shapes for /api/v1/exchange-rates. Rules mirror mobile src/validation/exchange-rates.ts. */
public final class ExchangeRateDtos {

    private ExchangeRateDtos() {}

    /** A rate is one unit of {@code fromCurrency} expressed in {@code toCurrency}: DECIMAL(18,8), > 0. */
    public record UpsertExchangeRateRequest(
            @NotNull(message = "Required") @Pattern(regexp = "[A-Z]{3}", message = "Enter a 3-letter currency code")
            String fromCurrency,
            @NotNull(message = "Required") @Pattern(regexp = "[A-Z]{3}", message = "Enter a 3-letter currency code")
            String toCurrency,
            @NotNull(message = "Required")
            @DecimalMin(value = "0", inclusive = false, message = "Rate must be greater than zero")
            @Digits(integer = 10, fraction = 8, message = "Enter a valid rate")
            BigDecimal rate,
            @NotNull(message = "Required") LocalDate effectiveFrom) {
        public UpsertExchangeRateRequest {
            fromCurrency = Inputs.currency(fromCurrency);
            toCurrency = Inputs.currency(toCurrency);
        }
    }

    public record ExchangeRateResponse(String id, String fromCurrency, String toCurrency,
                                       @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal rate,
                                       LocalDate effectiveFrom) {
        public static ExchangeRateResponse of(ExchangeRate r) {
            return new ExchangeRateResponse(r.getId(), r.getFromCurrency(), r.getToCurrency(), r.getRate(),
                    r.getEffectiveFrom());
        }
    }
}
