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

    @MockitoBean AuthService authService;

    @Test
    void signUpNameIsCappedLikeTheProfileName() throws Exception {
        mvc.perform(post("/api/auth/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","email":"a@example.test","password":"long-enough-password"}""".formatted("x".repeat(81))))
           .andExpect(status().is(422))
           .andExpect(jsonPath("$.error.fieldErrors.name[0]").value("Keep this under 80 characters"));
        verify(authService, never()).signUp(any());
    }
}
