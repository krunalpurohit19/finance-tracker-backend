package com.financetracker.api.exception;

import com.financetracker.api.dto.ApiEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler that maps every failure to the envelope the mobile
 * client expects: { ok: false, error: { code, message, fieldErrors? } }.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiEnvelope.Error> handleApiException(ApiException ex) {
        var body = ApiEnvelope.ErrorBody.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .fieldErrors(ex.getFieldErrors())
                .build();
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiEnvelope.Error.builder().error(body).build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope.Error> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.computeIfAbsent(fe.getField(), k -> new ArrayList<>()).add(fe.getDefaultMessage());
        }
        var body = ApiEnvelope.ErrorBody.builder()
                .code("VALIDATION_FAILED")
                .message("Some of the details you entered need fixing")
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(422)
                .body(ApiEnvelope.Error.builder().error(body).build());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiEnvelope.Error> handleNotFound(NoResourceFoundException ex) {
        var body = ApiEnvelope.ErrorBody.builder()
                .code("NOT_FOUND")
                .message("That endpoint doesn't exist")
                .build();
        return ResponseEntity.status(404)
                .body(ApiEnvelope.Error.builder().error(body).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope.Error> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        var body = ApiEnvelope.ErrorBody.builder()
                .code("INTERNAL")
                .message("Something went wrong on our end")
                .build();
        return ResponseEntity.status(500)
                .body(ApiEnvelope.Error.builder().error(body).build());
    }
}
