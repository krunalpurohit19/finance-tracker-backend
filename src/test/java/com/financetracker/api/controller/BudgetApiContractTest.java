package com.financetracker.api.controller;

import com.financetracker.api.entity.Budget;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.repository.BudgetRepository;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.service.BudgetService;
import com.financetracker.api.support.WebSliceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the JSON contract of /api/v1/budgets. Bodies are compared STRICTLY: the mobile budgets
 * screen reads amount/spent/remaining as money strings and percentUsed as a number.
 */
@WebMvcTest(BudgetController.class)
@Import(BudgetService.class)
class BudgetApiContractTest extends WebSliceTest {

    @MockitoBean BudgetRepository budgetRepo;
    @MockitoBean TransactionRepository txRepo;
    @MockitoBean CategoryRepository categoryRepo;

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

    Category food;

    @BeforeEach
    void setUp() {
        food = Category.builder().id("c-food").name("Food").kind(CategoryKind.EXPENSE).color("#FF7A00").build();
        when(categoryRepo.findByIdAndUserIdAndDeletedAtIsNull("c-food", USER_ID)).thenReturn(Optional.of(food));
        when(budgetRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ResultActions send(MockHttpServletRequestBuilder req, String json) throws Exception {
        return mvc.perform(req.with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static Budget budget(String id, Category category, String amount, LocalDate from, LocalDate to) {
        return Budget.builder().id(id).category(category).amount(new BigDecimal(amount))
                .effectiveFrom(from).effectiveTo(to).build();
    }

    // ── GET /api/v1/budgets ─────────────────────────────────────────────

    @Test
    void listComputesSpentRemainingPercentAndStatus() throws Exception {
        Category rent = Category.builder().id("c-rent").name("Rent").kind(CategoryKind.EXPENSE).build(); // no colour
        Category fun = Category.builder().id("c-fun").name("Fun").kind(CategoryKind.EXPENSE).color("#00AAFF").build();
        when(budgetRepo.findEffectiveForMonth(USER_ID, SEP_1, SEP_30)).thenReturn(List.of(
                budget("b-all", null, "800.0000", SEP_1, null),
                budget("b-food", food, "500.0000", SEP_1, LocalDate.of(2026, 12, 1)),
                budget("b-rent", rent, "1000.0000", SEP_1, null),
                budget("b-fun", fun, "0.0000", SEP_1, null)));
        when(txRepo.sumExpenseForBudget(USER_ID, null, SEP_1, SEP_30)).thenReturn(new BigDecimal("900.0000"));
        when(txRepo.sumExpenseForBudget(USER_ID, "c-food", SEP_1, SEP_30)).thenReturn(new BigDecimal("450.0000"));
        when(txRepo.sumExpenseForBudget(USER_ID, "c-rent", SEP_1, SEP_30)).thenReturn(new BigDecimal("100.0000"));
        when(txRepo.sumExpenseForBudget(USER_ID, "c-fun", SEP_1, SEP_30)).thenReturn(new BigDecimal("5.0000"));

        mvc.perform(get("/api/v1/budgets").param("month", "2026-09").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("""
                {"ok":true,"data":[
                  {"id":"b-all","amount":"800.0000","period":"MONTHLY","effectiveFrom":"2026-09-01",
                   "spent":"900.0000","remaining":"-100.0000","categoryName":"Overall Budget","month":"2026-09",
                   "percentUsed":112.5,"status":"EXCEEDED"},
                  {"id":"b-food","categoryId":"c-food","amount":"500.0000","period":"MONTHLY",
                   "effectiveFrom":"2026-09-01","effectiveTo":"2026-12-01",
                   "spent":"450.0000","remaining":"50.0000","categoryName":"Food","color":"#FF7A00","month":"2026-09",
                   "percentUsed":90.0,"status":"NEAR_LIMIT"},
                  {"id":"b-rent","categoryId":"c-rent","amount":"1000.0000","period":"MONTHLY","effectiveFrom":"2026-09-01",
                   "spent":"100.0000","remaining":"900.0000","categoryName":"Rent","month":"2026-09",
                   "percentUsed":10.0,"status":"OK"},
                  {"id":"b-fun","categoryId":"c-fun","amount":"0.0000","period":"MONTHLY","effectiveFrom":"2026-09-01",
                   "spent":"5.0000","remaining":"-5.0000","categoryName":"Fun","color":"#00AAFF","month":"2026-09",
                   "status":"OK"}]}""", JsonCompareMode.STRICT));
    }

    @Test
    void listDefaultsToTheCurrentMonth() throws Exception {
        YearMonth now = YearMonth.now();
        when(budgetRepo.findEffectiveForMonth(USER_ID, now.atDay(1), now.atEndOfMonth()))
                .thenReturn(List.of(budget("b-all", null, "100.0000", now.atDay(1), null)));
        when(txRepo.sumExpenseForBudget(USER_ID, null, now.atDay(1), now.atEndOfMonth())).thenReturn(BigDecimal.ZERO);

        mvc.perform(get("/api/v1/budgets").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[0].month").value(now.toString()))
           .andExpect(jsonPath("$.data[0].spent").value("0"))
           .andExpect(jsonPath("$.data[0].percentUsed").value(0.0));
    }

    // ── GET /api/v1/budgets/history ─────────────────────────────────────

    @Test
    void historyListsSpendPerMonthNewestFirst() throws Exception {
        YearMonth now = YearMonth.now();
        for (int i = 0; i < 3; i++) {
            YearMonth ym = now.minusMonths(i);
            when(txRepo.sumExpenseForBudget(USER_ID, "c-food", ym.atDay(1), ym.atEndOfMonth()))
                    .thenReturn(new BigDecimal((i + 1) + "0.0000"));
        }
        mvc.perform(get("/api/v1/budgets/history").param("months", "3").param("categoryId", "c-food").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("""
                {"ok":true,"data":[{"month":"%s","spent":"10.0000"},{"month":"%s","spent":"20.0000"},
                                   {"month":"%s","spent":"30.0000"}]}"""
                   .formatted(now, now.minusMonths(1), now.minusMonths(2)), JsonCompareMode.STRICT));
    }

    // ── POST /api/v1/budgets ────────────────────────────────────────────

    @Test
    void createReturns201WithTheBudget() throws Exception {
        var r = send(post("/api/v1/budgets"), """
                {"categoryId":"c-food","amount":"450.0000","effectiveFrom":"2026-09-01"}""");
        var saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepo).save(saved.capture());
        r.andExpect(status().isCreated())
         .andExpect(content().json("""
                {"ok":true,"data":{"id":"%s","categoryId":"c-food","amount":"450.0000","period":"MONTHLY",
                                   "effectiveFrom":"2026-09-01"}}""".formatted(saved.getValue().getId()), JsonCompareMode.STRICT));
        assertThat(saved.getValue().getCategory()).isSameAs(food);
    }

    @Test
    void createOverallBudgetWithEndDate() throws Exception {
        var r = send(post("/api/v1/budgets"), """
                {"amount":"2000.0000","effectiveFrom":"2026-09-01","effectiveTo":"2026-12-31"}""");
        var saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepo).save(saved.capture());
        r.andExpect(status().isCreated())
         .andExpect(content().json("""
                {"ok":true,"data":{"id":"%s","amount":"2000.0000","period":"MONTHLY",
                                   "effectiveFrom":"2026-09-01","effectiveTo":"2026-12-31"}}""".formatted(saved.getValue().getId()),
                 JsonCompareMode.STRICT));
    }

    @Test
    void createRejectsOverlap() throws Exception {
        when(budgetRepo.countOverlapping(eq(USER_ID), eq("c-food"), any(), eq(SEP_1), eq(LocalDate.of(9999, 12, 31))))
                .thenReturn(1L);
        send(post("/api/v1/budgets"), """
                {"categoryId":"c-food","amount":"450.0000","effectiveFrom":"2026-09-01"}""")
            .andExpect(status().isConflict())
            .andExpect(content().json("""
                {"ok":false,"error":{"code":"BUDGET_OVERLAP","message":"A budget already covers that period"}}""",
                 JsonCompareMode.STRICT));
    }

    // ── PATCH / DELETE ──────────────────────────────────────────────────

    @Test
    void updateChangesAmountAndEnd() throws Exception {
        Budget b = budget("b-food", food, "500.0000", SEP_1, null);
        when(budgetRepo.findByIdAndUserIdAndDeletedAtIsNull("b-food", USER_ID)).thenReturn(Optional.of(b));

        send(patch("/api/v1/budgets/b-food"), """
                {"amount":"650.0000","effectiveTo":"2026-12-01"}""")
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"ok":true,"data":{"id":"b-food","categoryId":"c-food","amount":"650.0000","period":"MONTHLY",
                                   "effectiveFrom":"2026-09-01","effectiveTo":"2026-12-01"}}""", JsonCompareMode.STRICT));
        assertThat(b.getAmount()).isEqualByComparingTo("650");
    }

    @Test
    void updateUnknownBudgetIs404() throws Exception {
        send(patch("/api/v1/budgets/nope"), "{\"amount\":\"1.0000\"}")
            .andExpect(status().isNotFound())
            .andExpect(content().json("""
                {"ok":false,"error":{"code":"NOT_FOUND","message":"Budget not found"}}""", JsonCompareMode.STRICT));
    }

    @Test
    void deleteSoftDeletes() throws Exception {
        Budget b = budget("b-food", food, "500.0000", SEP_1, null);
        when(budgetRepo.findByIdAndUserIdAndDeletedAtIsNull("b-food", USER_ID)).thenReturn(Optional.of(b));

        mvc.perform(delete("/api/v1/budgets/b-food").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("{\"ok\":true,\"data\":{\"id\":\"b-food\"}}", JsonCompareMode.STRICT));
        assertThat(b.getDeletedAt()).isNotNull();
    }

    // ── Input validation: 422 VALIDATION_FAILED with field errors (was 500) ──

    private void expectFieldError(ResultActions r, String field, String message) throws Exception {
        r.andExpect(status().is(422))
         .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
         .andExpect(jsonPath("$.error.fieldErrors['" + field + "'][0]").value(message));
    }

    @Test
    void createValidatesAmount() throws Exception {
        String tail = ",\"effectiveFrom\":\"2026-09-01\"}";
        expectFieldError(send(post("/api/v1/budgets"), "{\"effectiveFrom\":\"2026-09-01\"}"), "amount", "Enter an amount");
        expectFieldError(send(post("/api/v1/budgets"), "{\"amount\":\"0\"" + tail), "amount", "Amount must be greater than zero");
        expectFieldError(send(post("/api/v1/budgets"), "{\"amount\":\"-5\"" + tail), "amount", "Amount must be greater than zero");
        expectFieldError(send(post("/api/v1/budgets"), "{\"amount\":\"1.23456\"" + tail), "amount",
                "Enter a valid amount with up to 4 decimal places");
        expectFieldError(send(post("/api/v1/budgets"), "{\"amount\":\"lots\"" + tail), "amount", "Invalid value");
        expectFieldError(send(post("/api/v1/budgets"), "{\"amount\":\"1\",\"categoryId\":\"\"" + tail), "categoryId", "Choose a category");
        verify(budgetRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createWithUnknownCategoryIs404() throws Exception {
        send(post("/api/v1/budgets"), """
                {"categoryId":"someone-elses","amount":"1","effectiveFrom":"2026-09-01"}""")
            .andExpect(status().isNotFound())
            .andExpect(content().json("""
                {"ok":false,"error":{"code":"NOT_FOUND","message":"Category not found"}}""", JsonCompareMode.STRICT));
        verify(budgetRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateValidatesAmount() throws Exception {
        expectFieldError(send(patch("/api/v1/budgets/b-food"), "{\"amount\":\"0\"}"), "amount", "Amount must be greater than zero");
    }

    @Test
    void queryParamsAreValidated() throws Exception {
        for (String months : List.of("0", "25")) {
            expectFieldError(mvc.perform(get("/api/v1/budgets/history").param("months", months).with(asUser())),
                    "months", "Between 1 and 24 months");
        }
        expectFieldError(mvc.perform(get("/api/v1/budgets/history").param("months", "six").with(asUser())), "months", "Invalid value");
        expectFieldError(mvc.perform(get("/api/v1/budgets").param("month", "2026-13").with(asUser())), "month", "Invalid value");
    }
}
