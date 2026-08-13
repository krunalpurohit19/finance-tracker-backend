package com.financetracker.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * Matches the existing API envelope exactly:
 * { ok: true, data: T } | { ok: false, error: { code, message, fieldErrors? } }
 */
public class ApiEnvelope {

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class Success<T> {
        private final boolean ok = true;
        private T data;
    }

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class Error {
        private final boolean ok = false;
        private ErrorBody error;
    }

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorBody {
        private String code;
        private String message;
        private Map<String, List<String>> fieldErrors;
    }
}
