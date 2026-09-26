package com.financetracker.api.controller;

import com.financetracker.api.entity.GoalContribution;
import com.financetracker.api.entity.SavingsGoal;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.GoalStatus;
import com.financetracker.api.repository.GoalContributionRepository;
import com.financetracker.api.repository.SavingsGoalRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.support.WebSliceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
 * Pins the JSON contract of /api/v1/savings. Bodies are compared STRICTLY. Goal timing
 * (monthsRemaining/requiredMonthly/overdue) is relative to today, so target dates are too.
 */
@WebMvcTest(SavingsController.class)
class SavingsApiContractTest extends WebSliceTest {

    @MockitoBean SavingsGoalRepository goalRepo;
    @MockitoBean GoalContributionRepository contribRepo;
    @MockitoBean TransactionRepository txRepo;
    @MockitoBean UserSettingsRepository settingsRepo;

    static final LocalDate EPOCH_START = LocalDate.of(1970, 1, 1);
    static final LocalDate END_OF_TIME = LocalDate.of(9999, 12, 31);
    final YearMonth thisMonth = YearMonth.now();

    @BeforeEach
    void setUp() {
        when(settingsRepo.findById(USER_ID)).thenReturn(Optional.of(UserSettings.builder().userId(USER_ID).build()));
        when(contribRepo.sumByGoalId(anyString())).thenReturn(new BigDecimal("0.0000"));
        when(goalRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contribRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ResultActions send(MockHttpServletRequestBuilder req, String json) throws Exception {
        return mvc.perform(req.with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static List<Object[]> row(String a, String b) {
        return List.<Object[]>of(new Object[]{new BigDecimal(a), new BigDecimal(b)});
    }

    private static Object[] month(String month, String income, String expenses) {
        return new Object[]{month, new BigDecimal(income), new BigDecimal(expenses)};
    }

    private static SavingsGoal goal(String id, String target, LocalDate targetDate, GoalStatus status) {
        return SavingsGoal.builder().id(id).name("Goal " + id).targetAmount(new BigDecimal(target))
                .targetDate(targetDate).status(status).build();
    }

    // ── GET /api/v1/savings ─────────────────────────────────────────────

    @Test
    void summaryHasLifetimeThisMonthTrendBestAndWorst() throws Exception {
        when(txRepo.sumIncomeAndExpense(USER_ID, EPOCH_START, END_OF_TIME)).thenReturn(row("10000.0000", "6000.0000"));
        when(txRepo.sumIncomeAndExpense(USER_ID, thisMonth.atDay(1), thisMonth.atEndOfMonth())).thenReturn(row("2000.0000", "1500.0000"));
        when(txRepo.monthlyTrend(USER_ID, thisMonth.minusMonths(11).atDay(1), thisMonth.atEndOfMonth())).thenReturn(List.of(
                month("2026-07", "1000.0000", "1200.0000"),
                month("2026-08", "0.0000", "300.0000"),
                month("2026-09", "2000.0000", "1400.0000")));

        mvc.perform(get("/api/v1/savings").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("""
                {"ok":true,"data":{"baseCurrency":"INR","lifetime":"4000.0000","thisMonth":"500.0000",
                  "thisMonthRate":25.0,"averageMonthly":"33.33",
                  "best":{"month":"2026-09","saved":"600.0000"},"worst":{"month":"2026-08","saved":"-300.0000"},
                  "trend":[
                    {"month":"2026-07","income":"1000.0000","expenses":"1200.0000","saved":"-200.0000","rate":-20.0},
                    {"month":"2026-08","income":"0.0000","expenses":"300.0000","saved":"-300.0000","rate":-1.0},
                    {"month":"2026-09","income":"2000.0000","expenses":"1400.0000","saved":"600.0000","rate":30.0}]}}""",
                   JsonCompareMode.STRICT));
    }

    @Test
    void summaryWithNoTransactions() throws Exception {
        when(txRepo.sumIncomeAndExpense(any(), any(), any())).thenReturn(row("0.0000", "0.0000"));

        mvc.perform(get("/api/v1/savings").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("""
                {"ok":true,"data":{"baseCurrency":"INR","lifetime":"0.0000","thisMonth":"0.0000","trend":[]}}""",
                   JsonCompareMode.STRICT));
    }

    // ── Goals ───────────────────────────────────────────────────────────

    @Test
    void goalsReportProgressAndTiming() throws Exception {
        LocalDate inFiveMonths = thisMonth.plusMonths(5).atDay(15);
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        SavingsGoal house = goal("g-house", "10000.0000", inFiveMonths, GoalStatus.ACTIVE);
        house.setColor("#22AA88");
        house.setIcon("home");
        when(goalRepo.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(
                house,
                goal("g-bike", "500.0000", null, GoalStatus.ACTIVE),
                goal("g-trip", "1000.0000", lastMonth, GoalStatus.ARCHIVED)));
        when(contribRepo.sumByGoalId("g-house")).thenReturn(new BigDecimal("2500.0000"));
        when(contribRepo.sumByGoalId("g-bike")).thenReturn(new BigDecimal("600.0000"));
        when(contribRepo.sumByGoalId("g-trip")).thenReturn(new BigDecimal("100.0000"));

        mvc.perform(get("/api/v1/savings/goals").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("""
                {"ok":true,"data":[
                  {"id":"g-house","name":"Goal g-house","targetAmount":"10000.0000","saved":"2500.0000",
                   "remaining":"7500.0000","progress":25.0,"targetDate":"%s","monthsRemaining":5,
                   "requiredMonthly":"1500.00","overdue":false,"achieved":false,"color":"#22AA88","icon":"home",
                   "status":"ACTIVE","archived":false},
                  {"id":"g-bike","name":"Goal g-bike","targetAmount":"500.0000","saved":"600.0000","remaining":"0",
                   "progress":120.0,"overdue":false,"achieved":true,"status":"ACTIVE","archived":false},
                  {"id":"g-trip","name":"Goal g-trip","targetAmount":"1000.0000","saved":"100.0000",
                   "remaining":"900.0000","progress":10.0,"targetDate":"%s","overdue":true,"achieved":false,
                   "status":"ARCHIVED","archived":true}]}""".formatted(inFiveMonths, lastMonth), JsonCompareMode.STRICT));
    }

    @Test
    void createGoalReturns201() throws Exception {
        LocalDate target = thisMonth.plusMonths(4).atDay(1);
        var r = send(post("/api/v1/savings/goals"), """
                {"name":"Emergency fund","targetAmount":"12000.0000","targetDate":"%s"}""".formatted(target));
        var saved = ArgumentCaptor.forClass(SavingsGoal.class);
        verify(goalRepo).save(saved.capture());
        r.andExpect(status().isCreated())
         .andExpect(content().json("""
                {"ok":true,"data":{"id":"%s","name":"Emergency fund","targetAmount":"12000.0000","saved":"0.0000",
                  "remaining":"12000.0000","progress":0.0,"targetDate":"%s","monthsRemaining":4,
                  "requiredMonthly":"3000.00","overdue":false,"achieved":false,"status":"ACTIVE","archived":false}}"""
                 .formatted(saved.getValue().getId(), target), JsonCompareMode.STRICT));
    }

    @Test
    void getUnknownGoalIs404() throws Exception {
        mvc.perform(get("/api/v1/savings/goals/nope").with(asUser()))
           .andExpect(status().isNotFound())
           .andExpect(content().json("""
                {"ok":false,"error":{"code":"NOT_FOUND","message":"Goal not found"}}""", JsonCompareMode.STRICT));
    }

    @Test
    void patchGoalAppliesSentFieldsAndNullClearsTheDate() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", LocalDate.of(2027, 1, 1), GoalStatus.ACTIVE);
        g.setColor("#111111");
        when(goalRepo.findByIdAndUserIdAndDeletedAtIsNull("g1", USER_ID)).thenReturn(Optional.of(g));

        send(patch("/api/v1/savings/goals/g1"), """
                {"name":"Car","targetAmount":"2000.0000","targetDate":null,"icon":"car"}""")
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"ok":true,"data":{"id":"g1","name":"Car","targetAmount":"2000.0000","saved":"0.0000",
                  "remaining":"2000.0000","progress":0.0,"overdue":false,"achieved":false,"color":"#111111",
                  "icon":"car","status":"ACTIVE","archived":false}}""", JsonCompareMode.STRICT));
        assertThat(g.getTargetDate()).isNull();
    }

    @Test
    void archiveGoal() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        when(goalRepo.findByIdAndUserIdAndDeletedAtIsNull("g1", USER_ID)).thenReturn(Optional.of(g));

        mvc.perform(post("/api/v1/savings/goals/g1/archive").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
           .andExpect(jsonPath("$.data.archived").value(true));
        assertThat(g.getArchivedAt()).isNotNull();
    }

    @Test
    void deleteGoalSoftDeletes() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        when(goalRepo.findByIdAndUserIdAndDeletedAtIsNull("g1", USER_ID)).thenReturn(Optional.of(g));

        mvc.perform(delete("/api/v1/savings/goals/g1").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("{\"ok\":true,\"data\":{\"id\":\"g1\"}}", JsonCompareMode.STRICT));
        assertThat(g.getDeletedAt()).isNotNull();
    }

    // ── Contributions ───────────────────────────────────────────────────

    @Test
    void listContributions() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        when(goalRepo.findByIdAndUserIdAndDeletedAtIsNull("g1", USER_ID)).thenReturn(Optional.of(g));
        when(contribRepo.findByGoalIdAndDeletedAtIsNullOrderByOccurredOnDesc("g1")).thenReturn(List.of(
                GoalContribution.builder().id("c1").goal(g).amount(new BigDecimal("250.0000"))
                        .occurredOn(LocalDate.of(2026, 9, 10)).notes("Bonus")
                        .createdAt(Instant.parse("2026-09-10T08:30:00Z")).build(),
                GoalContribution.builder().id("c2").goal(g).amount(new BigDecimal("-50.0000"))
                        .occurredOn(LocalDate.of(2026, 9, 1)).createdAt(Instant.parse("2026-09-01T00:00:00Z")).build()));

        mvc.perform(get("/api/v1/savings/goals/g1/contributions").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("""
                {"ok":true,"data":[
                  {"id":"c1","amount":"250.0000","occurredOn":"2026-09-10","notes":"Bonus","createdAt":"2026-09-10T08:30:00Z"},
                  {"id":"c2","amount":"-50.0000","occurredOn":"2026-09-01","createdAt":"2026-09-01T00:00:00Z"}]}""",
                   JsonCompareMode.STRICT));
    }

    @Test
    void contributeReturns201() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        when(goalRepo.findByIdAndUserIdAndDeletedAtIsNull("g1", USER_ID)).thenReturn(Optional.of(g));

        var r = send(post("/api/v1/savings/goals/g1/contributions"), """
                {"amount":"250.0000","occurredOn":"2026-09-10","notes":"Bonus"}""");
        var saved = ArgumentCaptor.forClass(GoalContribution.class);
        verify(contribRepo).save(saved.capture());
        r.andExpect(status().isCreated())
         .andExpect(content().json("""
                {"ok":true,"data":{"id":"%s","amount":"250.0000","occurredOn":"2026-09-10","notes":"Bonus"}}"""
                 .formatted(saved.getValue().getId()), JsonCompareMode.STRICT));
        assertThat(saved.getValue().getGoal()).isSameAs(g);
    }

    @Test
    void contributingToAnArchivedGoalIs409() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ARCHIVED);
        when(goalRepo.findByIdAndUserIdAndDeletedAtIsNull("g1", USER_ID)).thenReturn(Optional.of(g));

        send(post("/api/v1/savings/goals/g1/contributions"), """
                {"amount":"250.0000","occurredOn":"2026-09-10"}""")
            .andExpect(status().isConflict())
            .andExpect(content().json("""
                {"ok":false,"error":{"code":"GOAL_ARCHIVED","message":"This goal is archived"}}""", JsonCompareMode.STRICT));
    }

    @Test
    void deleteContribution() throws Exception {
        GoalContribution c = GoalContribution.builder().id("c1").amount(BigDecimal.ONE).occurredOn(LocalDate.EPOCH).build();
        when(contribRepo.findByIdAndUserIdAndDeletedAtIsNull("c1", USER_ID)).thenReturn(Optional.of(c));

        mvc.perform(delete("/api/v1/savings/contributions/c1").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("{\"ok\":true,\"data\":{\"id\":\"c1\"}}", JsonCompareMode.STRICT));
        assertThat(c.getDeletedAt()).isNotNull();

        mvc.perform(delete("/api/v1/savings/contributions/nope").with(asUser()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error.message").value("Contribution not found"));
    }
}
