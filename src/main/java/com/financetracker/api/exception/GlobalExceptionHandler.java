package com.financetracker.api.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.financetracker.api.dto.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler that maps every failure to the envelope the mobile
 * client expects: { ok: false, error: { code, message, fieldErrors? } }.
 *
 * Every client input problem (bad JSON, wrong type, missing param, failed
 * constraint) is 422 VALIDATION_FAILED — the project never returns 400.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_MESSAGE = "Some of the details you entered need fixing";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiEnvelope.Error> handleApiException(ApiException ex) {
        return error(ex.getHttpStatus(), ex.getCode(), ex.getMessage(), ex.getFieldErrors());
    }

    /** {@code @Valid @RequestBody} failures. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope.Error> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            add(fieldErrors, fe.getField(), fe.getDefaultMessage());
        }
        return invalid(req, ex, VALIDATION_MESSAGE, fieldErrors);
    }

    /** Constraints on {@code @RequestParam}/{@code @PathVariable} (Spring's built-in method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiEnvelope.Error> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        if (ex.isForReturnValue()) return handleUnexpected(ex, req); // our bug, not the client's
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                // Elements of a @Valid List/Map body: key as "[0].amount", matching the Jackson path style.
                String prefix = errors.getContainerIndex() != null ? "[" + errors.getContainerIndex() + "]."
                        : errors.getContainerKey() != null ? errors.getContainerKey() + "." : "";
                errors.getFieldErrors().forEach(fe -> add(fieldErrors, prefix + fe.getField(), fe.getDefaultMessage()));
            } else {
                String name = paramName(result.getMethodParameter());
                result.getResolvableErrors().forEach(e -> add(fieldErrors, name, e.getDefaultMessage()));
            }
        }
        return invalid(req, ex, VALIDATION_MESSAGE, fieldErrors);
    }

    /** Malformed JSON, wrong JSON types, unknown enum values, missing body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnvelope.Error> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        if (ex.getCause() instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            return invalid(req, ex, VALIDATION_MESSAGE, Map.of(jsonPath(jme.getPath()), List.of("Invalid value")));
        }
        return invalid(req, ex, "The request body couldn't be read", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiEnvelope.Error> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return invalid(req, ex, VALIDATION_MESSAGE, Map.of(ex.getName(), List.of("Invalid value")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiEnvelope.Error> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return invalid(req, ex, VALIDATION_MESSAGE, Map.of(ex.getParameterName(), List.of("Required")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope.Error> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Spring MVC's own exceptions (404 no route, 405, 415, missing header, ...) carry their status; keep it,
        // except 400, because the project reports input problems as 422.
        if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            int status = er.getStatusCode().value() == 400 ? 422 : er.getStatusCode().value();
            boolean noRoute = status == 404 || status == 405;
            log.warn("{} {} {} ({})", status, req.getMethod(), req.getRequestURI(), ex.getClass().getSimpleName());
            return ResponseEntity.status(status).headers(er.getHeaders()).body(envelope(
                    noRoute ? "NOT_FOUND" : "VALIDATION_FAILED",
                    noRoute ? "That endpoint doesn't exist" : "The request couldn't be processed", null));
        }
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return error(500, "INTERNAL", "Something went wrong on our end", null);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static ResponseEntity<ApiEnvelope.Error> error(int status, String code, String message,
                                                           Map<String, List<String>> fieldErrors) {
        return ResponseEntity.status(status).body(envelope(code, message, fieldErrors));
    }

    /**
     * 422 for a client input problem. Logged without the offending values (they can be personal data)
     * so that drift between the mobile client and the API is visible server-side.
     */
    private static ResponseEntity<ApiEnvelope.Error> invalid(HttpServletRequest req, Exception ex, String message,
                                                             Map<String, List<String>> fieldErrors) {
        log.warn("422 {} {} ({}) fields={}", req.getMethod(), req.getRequestURI(), ex.getClass().getSimpleName(),
                fieldErrors == null ? List.of() : fieldErrors.keySet());
        return error(422, "VALIDATION_FAILED", message, fieldErrors);
    }

    private static ApiEnvelope.Error envelope(String code, String message, Map<String, List<String>> fieldErrors) {
        var body = ApiEnvelope.ErrorBody.builder().code(code).message(message).fieldErrors(fieldErrors).build();
        return ApiEnvelope.Error.builder().error(body).build();
    }

    private static void add(Map<String, List<String>> fieldErrors, String field, String message) {
        fieldErrors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
    }

    /** The name the client used: {@code @RequestParam("x")} / {@code @PathVariable("x")}, else the Java name. */
    private static String paramName(MethodParameter p) {
        RequestParam rp = p.getParameterAnnotation(RequestParam.class);
        if (rp != null && !rp.name().isEmpty()) return rp.name();
        PathVariable pv = p.getParameterAnnotation(PathVariable.class);
        if (pv != null && !pv.name().isEmpty()) return pv.name();
        return p.getParameterName();
    }

    /** Jackson reference path → "items[0].amount". */
    private static String jsonPath(List<JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (var ref : path) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) sb.append('.');
                sb.append(ref.getFieldName());
            } else {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }
}
