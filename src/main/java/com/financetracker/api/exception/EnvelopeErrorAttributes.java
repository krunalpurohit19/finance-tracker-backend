package com.financetracker.api.exception;

import com.financetracker.api.dto.ApiEnvelope;
import jakarta.servlet.RequestDispatcher;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * Body for Boot's /error fallback — errors that never reach {@link GlobalExceptionHandler}
 * (exceptions thrown in servlet filters, a failing exception handler, container errors).
 * Without this the client gets Boot's {timestamp, status, ...} map, which has no "ok"/"error".
 */
@Component
public class EnvelopeErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST);
        int status = raw instanceof Integer i ? i : 500;
        var error = switch (status) {
            case 401 -> body("UNAUTHENTICATED", "Sign in to continue");
            case 403 -> body("FORBIDDEN", "You can't do that");
            case 404, 405 -> body("NOT_FOUND", "That endpoint doesn't exist");
            default -> status < 500
                    ? body("VALIDATION_FAILED", "The request couldn't be processed")
                    : body("INTERNAL", "Something went wrong on our end");
        };
        return Map.of("ok", false, "error", error);
    }

    private static ApiEnvelope.ErrorBody body(String code, String message) {
        return ApiEnvelope.ErrorBody.builder().code(code).message(message).build();
    }
}
