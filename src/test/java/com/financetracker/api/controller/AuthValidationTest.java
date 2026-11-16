package com.financetracker.api.controller;

import com.financetracker.api.service.AuthService;
import com.financetracker.api.support.WebSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthValidationTest extends WebSliceTest {

    @MockitoBean com.financetracker.api.service.AccountRecoveryService recovery;
    @MockitoBean AuthService authService;

    @Test
    void signUpNameIsCappedLikeTheProfileName() throws Exception {
        mvc.perform(post("/api/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","email":"a@example.test","password":"long-enough-password"}""".formatted("x".repeat(81))))
           .andExpect(status().is(422))
           .andExpect(jsonPath("$.error.fieldErrors.name[0]").value("Keep this under 80 characters"));
        verify(authService, never()).signUp(any());
    }

    @Test
    void refreshHasItsOwnRateLimitAndForwardedForCannotDodgeIt() throws Exception {
        String ip = "203.0.113.7";
        for (int i = 0; i < 10; i++) signIn(ip, "198.51.100." + i).andExpect(status().is(422));
        // The sign-in budget is spent, whatever X-Forwarded-For claims…
        signIn(ip, "198.51.100.99").andExpect(status().is(429)).andExpect(jsonPath("$.ok").value(false));
        // …but a signed-in app on the same address can still refresh.
        mvc.perform(post("/api/auth/refresh").with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"t\"}"))
           .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions signIn(String ip, String forwardedFor) throws Exception {
        return mvc.perform(post("/api/auth/sign-in").with(r -> { r.setRemoteAddr(ip); return r; })
                .header("X-Forwarded-For", forwardedFor).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }
}
