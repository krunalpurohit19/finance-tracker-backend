package com.financetracker.api.dto;

import java.util.Locale;

/**
 * Normalisation applied in request records' compact constructors, so validation
 * annotations see the value exactly as it will be stored (mirrors the mobile Zod
 * schemas, which trim and upper-case before validating).
 */
public final class Inputs {

    private Inputs() {}

    public static String strip(String s) {
        return s == null ? null : s.strip();
    }

    /** ISO 4217 codes are stored upper-case. */
    public static String currency(String s) {
        return s == null ? null : s.strip().toUpperCase(Locale.ROOT);
    }
}
