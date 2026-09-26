package com.financetracker.api.exception;

import com.financetracker.api.support.WebSliceTest;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({GlobalExceptionHandlerTest.ProbeController.class, GlobalExceptionHandlerTest.AuthProbeController.class})
@Import({GlobalExceptionHandlerTest.ProbeController.class, GlobalExceptionHandlerTest.AuthProbeController.class}) // nested test classes are skipped by component scan
class GlobalExceptionHandlerTest extends WebSliceTest {

    enum Kind { INCOME, EXPENSE }

    record ProbeBody(@NotBlank(message = "Required") String name,
                     @NotNull(message = "Required") @DecimalMin(value = "0", inclusive = false, message = "Must be more than 0") BigDecimal amount,
                     Kind kind,
                     LocalDate on) {}

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {
        @PostMapping("/body")
        String body(@Valid @RequestBody ProbeBody body) { return "ok"; }

        @PostMapping("/mixed")
        String mixed(@RequestParam @Min(1) int size, @Valid @RequestBody ProbeBody body) { return "ok"; }

        @GetMapping("/params")
        String params(@RequestParam @Min(value = 1, message = "Must be at least 1") @Max(24) int months,
                      @RequestParam(name = "page-size", defaultValue = "10") @Min(value = 1, message = "Too small") int pageSize,
                      @RequestParam(required = false) LocalDate from) { return "ok"; }

        @PostMapping("/list")
        String list(@Valid @RequestBody List<ProbeBody> items) { return "ok"; }

        @GetMapping("/header")
        String header(@RequestHeader("X-Device") String device) { return "ok"; }

        @GetMapping("/return-value")
        @Min(5) int returnValue() { return 1; }

        @GetMapping("/conflict")
        String conflict() { throw ApiException.budgetOverlap("Already budgeted"); }

        @GetMapping("/boom")
        String boom() { throw new IllegalStateException("db password is hunter2"); }
    }

    @RestController
    static class AuthProbeController {
        @PostMapping("/api/auth/probe")
        String authProbe() { return "ok"; }
    }

    private static final String VALID = """
            {"name":"Rent","amount":"12.5000","kind":"EXPENSE","on":"2026-09-01"}""";

    private ResultActions postBody(String url, String json) throws Exception {
        return mvc.perform(post(url).with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static void expectValidationFailed(ResultActions r) throws Exception {
        r.andExpect(status().is(422))
         .andExpect(jsonPath("$.ok").value(false))
         .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void validBodyPasses() throws Exception {
        postBody("/probe/body", VALID).andExpect(status().isOk());
    }

    @Test
    void jsonNumberIsAcceptedForMoney() throws Exception {
        postBody("/probe/body", """
                {"name":"Rent","amount":12.5}""").andExpect(status().isOk());
    }

    @Test
    void beanValidationFailuresAreKeyedByField() throws Exception {
        var r = postBody("/probe/body", """
                {"name":"","amount":"-1"}""");
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors.name[0]").value("Required"))
         .andExpect(jsonPath("$.error.fieldErrors.amount[0]").value("Must be more than 0"));
    }

    @Test
    void unknownEnumValueIsFieldError() throws Exception {
        var r = postBody("/probe/body", """
                {"name":"Rent","amount":"1","kind":"GIFT"}""");
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors.kind[0]").value("Invalid value"));
    }

    @Test
    void malformedDateIsFieldError() throws Exception {
        var r = postBody("/probe/body", """
                {"name":"Rent","amount":"1","on":"01/09/2026"}""");
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors.on[0]").value("Invalid value"));
    }

    @Test
    void nonNumericMoneyIsFieldError() throws Exception {
        var r = postBody("/probe/body", """
                {"name":"Rent","amount":"twelve"}""");
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors.amount[0]").value("Invalid value"));
    }

    @Test
    void malformedJsonIs422WithoutFieldErrors() throws Exception {
        var r = postBody("/probe/body", "{\"name\":");
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.message").value("The request body couldn't be read"))
         .andExpect(jsonPath("$.error.fieldErrors").doesNotExist());
    }

    @Test
    void missingBodyIs422() throws Exception {
        expectValidationFailed(mvc.perform(post("/probe/body").with(asUser()).contentType(MediaType.APPLICATION_JSON)));
    }

    @Test
    void paramConstraintUsesRequestParamName() throws Exception {
        var r = mvc.perform(get("/probe/params").with(asUser()).param("months", "0").param("page-size", "0"));
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors.months[0]").value("Must be at least 1"))
         .andExpect(jsonPath("$.error.fieldErrors['page-size'][0]").value("Too small"));
    }

    @Test
    void paramAndBodyFailuresAreMerged() throws Exception {
        var r = mvc.perform(post("/probe/mixed").with(asUser()).param("size", "0")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"amount\":\"1\"}"));
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors.size").exists())
         .andExpect(jsonPath("$.error.fieldErrors.name[0]").value("Required"));
    }

    @Test
    void paramTypeMismatchIsFieldError() throws Exception {
        var r = mvc.perform(get("/probe/params").with(asUser()).param("months", "abc"));
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors.months[0]").value("Invalid value"));
    }

    @Test
    void malformedDateParamIsFieldError() throws Exception {
        var r = mvc.perform(get("/probe/params").with(asUser()).param("months", "1").param("from", "yesterday"));
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors.from[0]").value("Invalid value"))
         .andExpect(jsonPath("$.error.fieldErrors.months").doesNotExist());
    }

    @Test
    void listBodyErrorsAreKeyedByIndex() throws Exception {
        var r = postBody("/probe/list", """
                [{"name":"Rent","amount":"1"},{"name":"","amount":"1"}]""");
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors['[1].name'][0]").value("Required"))
         .andExpect(jsonPath("$.error.fieldErrors.name").doesNotExist());
    }

    @Test
    void frameworkBadRequestIs422NotBadRequest() throws Exception {
        var r = mvc.perform(get("/probe/header").with(asUser()));
        expectValidationFailed(r);
    }

    @Test
    void returnValueConstraintIsServerError() throws Exception {
        mvc.perform(get("/probe/return-value").with(asUser()))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.error.code").value("INTERNAL"));
    }

    @Test
    void errorDispatchRendersEnvelopeWithoutAuthentication() throws Exception {
        // What Tomcat does when a filter throws: forwards to /error with the status set.
        mvc.perform(get("/error").with(r -> {
               r.setDispatcherType(DispatcherType.ERROR);
               r.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
               return r;
           }))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.ok").value(false))
           .andExpect(jsonPath("$.error.code").value("INTERNAL"))
           .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    void authEndpointsAreNotRateLimitedAcrossTests() throws Exception {
        // Harness check: RateLimitFilter allows 10/min per IP; WebSliceTest gives each request its own IP.
        for (int i = 0; i < 12; i++) {
            mvc.perform(post("/api/auth/probe")).andExpect(status().isOk());
        }
    }

    @Test
    void missingRequiredParamIsFieldError() throws Exception {
        var r = mvc.perform(get("/probe/params").with(asUser()));
        expectValidationFailed(r);
        r.andExpect(jsonPath("$.error.fieldErrors.months[0]").value("Required"));
    }

    @Test
    void apiExceptionKeepsItsCodeAndStatus() throws Exception {
        mvc.perform(get("/probe/conflict").with(asUser()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.ok").value(false))
           .andExpect(jsonPath("$.error.code").value("BUDGET_OVERLAP"))
           .andExpect(jsonPath("$.error.message").value("Already budgeted"));
    }

    @Test
    void unexpectedErrorIs500AndDoesNotLeakDetails() throws Exception {
        mvc.perform(get("/probe/boom").with(asUser()))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.error.code").value("INTERNAL"))
           .andExpect(jsonPath("$.error.message").value(not(containsString("hunter2"))));
    }

    @Test
    void unknownRouteIs404Envelope() throws Exception {
        mvc.perform(get("/probe/nope").with(asUser()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.ok").value(false))
           .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodIs405EnvelopeWithAllowHeader() throws Exception {
        mvc.perform(delete("/probe/body").with(asUser()))
           .andExpect(status().isMethodNotAllowed())
           .andExpect(header().string("Allow", containsString("POST")))
           .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void wrongContentTypeIs415Envelope() throws Exception {
        mvc.perform(post("/probe/body").with(asUser()).contentType(MediaType.TEXT_PLAIN).content("hi"))
           .andExpect(status().isUnsupportedMediaType())
           .andExpect(jsonPath("$.ok").value(false))
           .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unauthenticatedIs401Envelope() throws Exception {
        mvc.perform(get("/probe/params").param("months", "1"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.ok").value(false))
           .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
