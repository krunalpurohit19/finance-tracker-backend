package com.financetracker.api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Percentages derived from money: BigDecimal, never double. Sent as JSON numbers (mobile calls toFixed). */
final class Ratios {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Ratios() {}

    /** {@code part / whole × 100} at 2 decimals, HALF_UP; null when {@code whole} is not positive. */
    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() > 0 ? part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP) : null;
    }
}
