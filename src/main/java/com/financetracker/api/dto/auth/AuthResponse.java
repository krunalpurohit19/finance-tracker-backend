package com.financetracker.api.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UserInfo user;

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class UserInfo {
        private String id;
        private String name;
        private String email;
    }
}
