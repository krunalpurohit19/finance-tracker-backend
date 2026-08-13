package com.financetracker.api.exception;

import lombok.Getter;
import java.util.List;
import java.util.Map;

/**
 * Application-level exception hierarchy. Each maps to an error code the mobile
 * client recognizes. HTTP status is derived from the code, matching ERROR_STATUS
 * in the original TypeScript domain package.
 */
@Getter
public class ApiException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final Map<String, List<String>> fieldErrors;

    public ApiException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    public ApiException(String code, int httpStatus, String message, Map<String, List<String>> fieldErrors) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.fieldErrors = fieldErrors;
    }

    // ── Factory methods matching the original ErrorCode union ──────────

    public static ApiException validationFailed(String message, Map<String, List<String>> fieldErrors) {
        return new ApiException("VALIDATION_FAILED", 422, message, fieldErrors);
    }

    public static ApiException unauthenticated(String message) {
        return new ApiException("UNAUTHENTICATED", 401, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException("NOT_FOUND", 404, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException("CONFLICT", 409, message);
    }

    public static ApiException accountArchived(String message) {
        return new ApiException("ACCOUNT_ARCHIVED", 409, message);
    }

    public static ApiException sameAccountTransfer() {
        return new ApiException("SAME_ACCOUNT_TRANSFER", 422, "Choose two different accounts");
    }

    public static ApiException currencyMismatch(String message) {
        return new ApiException("CURRENCY_MISMATCH", 422, message);
    }

    public static ApiException missingExchangeRate(String message) {
        return new ApiException("MISSING_EXCHANGE_RATE", 422, message);
    }

    public static ApiException categoryInUse(String message) {
        return new ApiException("CATEGORY_IN_USE", 409, message);
    }

    public static ApiException accountInUse(String message) {
        return new ApiException("ACCOUNT_IN_USE", 409, message);
    }

    public static ApiException budgetOverlap(String message) {
        return new ApiException("BUDGET_OVERLAP", 409, message);
    }

    public static ApiException goalArchived(String message) {
        return new ApiException("GOAL_ARCHIVED", 409, message);
    }

    public static ApiException internal(String message) {
        return new ApiException("INTERNAL", 500, message);
    }
}
