package com.financetracker.api.controller;

import com.financetracker.api.entity.GoalContribution;
import com.financetracker.api.entity.SavingsGoal;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.GoalStatus;
import com.financetracker.api.repository.GoalContributionRepository;
import com.financetracker.api.repository.SavingsGoalRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.service.SavingsService;
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
@Import(SavingsService.class)
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

    /** The goal as found by both the plain and the row-locking finder. */
    private void stubGoal(SavingsGoal g) {
        when(goalRepo.findByIdAndUserIdAndDeletedAtIsNull(g.getId(), USER_ID)).thenReturn(Optional.of(g));
        when(goalRepo.lockOwned(g.getId(), USER_ID)).thenReturn(Optional.of(g));
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
                    {"month":"2026-08","income":"0.0000","expenses":"300.0000","saved":"-300.0000","rate":null},
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
        when(contribRepo.sumByGoalIds(List.of("g-house", "g-bike", "g-trip"))).thenReturn(List.of(
                new Object[]{"g-house", new BigDecimal("2500.0000")}, new Object[]{"g-bike", new BigDecimal("600.0000")},
                new Object[]{"g-trip", new BigDecimal("100.0000")}));

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
                   "remaining":"900.0000","progress":10.0,"targetDate":"%s","requiredMonthly":"900.0000",
                   "overdue":true,"achieved":false,
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
        stubGoal(g);

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
        stubGoal(g);

        mvc.perform(post("/api/v1/savings/goals/g1/archive").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
           .andExpect(jsonPath("$.data.archived").value(true));
        assertThat(g.getArchivedAt()).isNotNull();
    }

    @Test
    void deleteGoalSoftDeletes() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        stubGoal(g);

        mvc.perform(delete("/api/v1/savings/goals/g1").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("{\"ok\":true,\"data\":{\"id\":\"g1\"}}", JsonCompareMode.STRICT));
        assertThat(g.getDeletedAt()).isNotNull();
    }

    // ── Contributions ───────────────────────────────────────────────────

    @Test
    void listContributions() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        stubGoal(g);
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
        stubGoal(g);

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
        stubGoal(g);

        send(post("/api/v1/savings/goals/g1/contributions"), """
                {"amount":"250.0000","occurredOn":"2026-09-10"}""")
            .andExpect(status().isConflict())
            .andExpect(content().json("""
                {"ok":false,"error":{"code":"GOAL_ARCHIVED","message":"This goal is archived"}}""", JsonCompareMode.STRICT));
    }

    @Test
    void deleteContribution() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        stubGoal(g);
        when(contribRepo.sumByGoalId("g1")).thenReturn(new BigDecimal("1.0000"));
        GoalContribution c = GoalContribution.builder().id("c1").goal(g).amount(BigDecimal.ONE).occurredOn(LocalDate.EPOCH).build();
        when(contribRepo.lockOwned("c1", USER_ID)).thenReturn(Optional.of(c));

        mvc.perform(delete("/api/v1/savings/contributions/c1").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("{\"ok\":true,\"data\":{\"id\":\"c1\"}}", JsonCompareMode.STRICT));
        assertThat(c.getDeletedAt()).isNotNull();

        mvc.perform(delete("/api/v1/savings/contributions/nope").with(asUser()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error.message").value("Contribution not found"));
    }

    // ── Input validation: 422 VALIDATION_FAILED with field errors (was 500 or silently stored) ──

    private void expectFieldError(ResultActions r, String field, String message) throws Exception {
        r.andExpect(status().is(422))
         .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
         .andExpect(jsonPath("$.error.fieldErrors['" + field + "'][0]").value(message));
    }

    @Test
    void createGoalValidatesEveryField() throws Exception {
        String url = "/api/v1/savings/goals";
        expectFieldError(send(post(url), "{\"targetAmount\":\"1\"}"), "name", "Required");
        expectFieldError(send(post(url), "{\"name\":\"  \",\"targetAmount\":\"1\"}"), "name", "Required");
        expectFieldError(send(post(url), "{\"name\":\"" + "x".repeat(61) + "\",\"targetAmount\":\"1\"}"), "name", "Keep this under 60 characters");
        expectFieldError(send(post(url), "{\"name\":\"Car\"}"), "targetAmount", "Enter an amount");
        expectFieldError(send(post(url), "{\"name\":\"Car\",\"targetAmount\":\"0\"}"), "targetAmount", "Amount must be greater than zero");
        expectFieldError(send(post(url), "{\"name\":\"Car\",\"targetAmount\":\"1.00001\"}"), "targetAmount",
                "Enter a valid amount with up to 4 decimal places");
        expectFieldError(send(post(url), "{\"name\":\"Car\",\"targetAmount\":\"1\",\"color\":\"red\"}"), "color", "Enter a colour as #RRGGBB");
        expectFieldError(send(post(url), "{\"name\":\"Car\",\"targetAmount\":\"1\",\"icon\":\"" + "i".repeat(41) + "\"}"),
                "icon", "Keep this under 40 characters");
        verify(goalRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void patchGoalValidatesAndLeavesAbsentFieldsAlone() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", LocalDate.of(2027, 1, 1), GoalStatus.ACTIVE);
        stubGoal(g);

        expectFieldError(send(patch("/api/v1/savings/goals/g1"), "{\"name\":\" \"}"), "name", "Required");
        expectFieldError(send(patch("/api/v1/savings/goals/g1"), "{\"targetAmount\":\"-1\"}"), "targetAmount", "Amount must be greater than zero");
        expectFieldError(send(patch("/api/v1/savings/goals/g1"), "{\"status\":\"DONE\"}"), "status", "Invalid value");

        send(patch("/api/v1/savings/goals/g1"), "{\"name\":\"Car\"}").andExpect(status().isOk());
        assertThat(g.getName()).isEqualTo("Car");
        assertThat(g.getTargetDate()).isEqualTo(LocalDate.of(2027, 1, 1)); // absent ≠ null
        assertThat(g.getTargetAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void contributionValidation() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        stubGoal(g);
        String url = "/api/v1/savings/goals/g1/contributions";

        expectFieldError(send(post(url), "{\"occurredOn\":\"2026-09-10\"}"), "amount", "Enter an amount");
        expectFieldError(send(post(url), "{\"amount\":\"0\",\"occurredOn\":\"2026-09-10\"}"), "amount",
                "Enter an amount above or below zero");
        expectFieldError(send(post(url), "{\"amount\":\"1\",\"occurredOn\":\"2026-09-10\",\"notes\":\"" + "n".repeat(201) + "\"}"),
                "notes", "Keep this under 200 characters");
        verify(contribRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void withdrawalsAreNegativeContributions() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        stubGoal(g);
        when(contribRepo.sumByGoalId("g1")).thenReturn(new BigDecimal("100.0000"));
        send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"-50\",\"occurredOn\":\"2026-09-10\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.amount").value("-50.0000"));
        expectFieldError(send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"-100.0001\"}"),
                "amount", "You can't withdraw more than you've saved toward this goal");
    }

    // ── Contract fixes ──

    @Test
    void contributeAsTheMobileAppSendsIt() throws Exception {
        // useContribute posts only {amount}; this used to be a 500 (occurredOn was required).
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        stubGoal(g);
        when(contribRepo.save(any())).thenAnswer(inv -> {
            GoalContribution in = inv.getArgument(0); // merge() returns a different, persisted instance
            return GoalContribution.builder().id(in.getId()).goal(in.getGoal()).amount(in.getAmount())
                    .occurredOn(in.getOccurredOn()).createdAt(Instant.parse("2026-09-27T10:00:00Z")).build();
        });

        send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"250\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.amount").value("250.0000"))
            .andExpect(jsonPath("$.data.occurredOn").value(LocalDate.now().toString()))
            .andExpect(jsonPath("$.data.createdAt").value("2026-09-27T10:00:00Z"));
    }

    @Test
    void goalsWithoutContributionsReportZeroAndNoGoalsMeansNoSumQuery() throws Exception {
        when(goalRepo.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(goal("g1", "300.0000", null, GoalStatus.ACTIVE)));
        mvc.perform(get("/api/v1/savings/goals").with(asUser()))
           .andExpect(jsonPath("$.data[0].saved").value("0.0000"))
           .andExpect(jsonPath("$.data[0].remaining").value("300.0000"));

        when(goalRepo.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        mvc.perform(get("/api/v1/savings/goals").with(asUser())).andExpect(jsonPath("$.data.length()").value(0));
        verify(contribRepo, org.mockito.Mockito.times(1)).sumByGoalIds(any());
    }

    @Test
    void bestAndWorstHandleAnyAmount() throws Exception {
        when(txRepo.sumIncomeAndExpense(any(), any(), any())).thenReturn(row("0.0000", "0.0000"));
        when(txRepo.monthlyTrend(USER_ID, thisMonth.minusMonths(11).atDay(1), thisMonth.atEndOfMonth())).thenReturn(List.of(
                month("2026-07", "250000000.0000", "100000000.0000"),   // +150,000,000: beyond the old sentinel
                month("2026-08", "100.0000", "200000100.0000")));       // -200,000,000
        mvc.perform(get("/api/v1/savings").with(asUser()))
           .andExpect(jsonPath("$.data.best.month").value("2026-07"))
           .andExpect(jsonPath("$.data.best.saved").value("150000000.0000"))
           .andExpect(jsonPath("$.data.worst.month").value("2026-08"));
    }

    @Test
    void archiveStateFollowsStatusPatches() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ARCHIVED);
        g.setArchivedAt(Instant.EPOCH);
        stubGoal(g);

        send(patch("/api/v1/savings/goals/g1"), "{\"status\":\"ACTIVE\"}").andExpect(jsonPath("$.data.archived").value(false));
        assertThat(g.getArchivedAt()).isNull();
        send(patch("/api/v1/savings/goals/g1"), "{\"status\":\"ARCHIVED\"}").andExpect(jsonPath("$.data.archived").value(true));
        assertThat(g.getArchivedAt()).isNotNull();
    }

    // ── Review follow-ups ──

    @Test
    void contributingLocksTheGoalRowBeforeSumming() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        stubGoal(g);
        send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"10\"}").andExpect(status().isCreated());
        var order = org.mockito.Mockito.inOrder(goalRepo, contribRepo);
        order.verify(goalRepo).lockOwned("g1", USER_ID);
        order.verify(contribRepo).sumByGoalId("g1");
        verify(goalRepo, org.mockito.Mockito.never()).findByIdAndUserIdAndDeletedAtIsNull(any(), any());
    }

    @Test
    void withdrawingEverythingSavedIsAllowed() throws Exception {
        stubGoal(goal("g1", "1000.0000", null, GoalStatus.ACTIVE));
        when(contribRepo.sumByGoalId("g1")).thenReturn(new BigDecimal("100.0000"));
        send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"-100\"}").andExpect(status().isCreated());
    }

    @Test
    void depositsCannotOverflowWhatTheAppCanShow() throws Exception {
        stubGoal(goal("g1", "1000.0000", null, GoalStatus.ACTIVE));
        when(contribRepo.sumByGoalId("g1")).thenReturn(new BigDecimal("99999999999999.0000"));
        expectFieldError(send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"1\"}"),
                "amount", "That's more than this goal can hold");
        send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"0.9999\"}").andExpect(status().isCreated());
    }

    @Test
    void removingADepositThatWithdrawalsDependOnIsRefused() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        stubGoal(g);
        when(contribRepo.sumByGoalId("g1")).thenReturn(new BigDecimal("0.0000")); // +100 then -100
        GoalContribution deposit = GoalContribution.builder().id("c1").goal(g).amount(new BigDecimal("100.0000"))
                .occurredOn(LocalDate.EPOCH).build();
        when(contribRepo.lockOwned("c1", USER_ID)).thenReturn(Optional.of(deposit));

        mvc.perform(delete("/api/v1/savings/contributions/c1").with(asUser()))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.error.message").value("Remove the withdrawals from this goal first"));
        assertThat(deposit.getDeletedAt()).isNull();
    }

    @Test
    void removingAWithdrawalOrAContributionOfADeletedGoalNeedsNoCheck() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE); // lockOwned → empty: goal deleted
        GoalContribution deposit = GoalContribution.builder().id("c1").goal(g).amount(new BigDecimal("100.0000"))
                .occurredOn(LocalDate.EPOCH).build();
        GoalContribution withdrawal = GoalContribution.builder().id("c2").goal(g).amount(new BigDecimal("-5.0000"))
                .occurredOn(LocalDate.EPOCH).build();
        when(contribRepo.lockOwned("c1", USER_ID)).thenReturn(Optional.of(deposit));
        when(contribRepo.lockOwned("c2", USER_ID)).thenReturn(Optional.of(withdrawal));

        mvc.perform(delete("/api/v1/savings/contributions/c1").with(asUser())).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/savings/contributions/c2").with(asUser())).andExpect(status().isOk());
        assertThat(deposit.getDeletedAt()).isNotNull();
        assertThat(withdrawal.getDeletedAt()).isNotNull();
        verify(contribRepo, org.mockito.Mockito.never()).sumByGoalId(any());
    }

    @Test
    void contributionsOfSomeoneElsesGoalAre404() throws Exception {
        mvc.perform(get("/api/v1/savings/goals/g-other/contributions").with(asUser()))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error.message").value("Goal not found"));
        verify(contribRepo, org.mockito.Mockito.never()).findByGoalIdAndDeletedAtIsNullOrderByOccurredOnDesc(any());
    }

    @Test
    void newRowsBelongToTheSignedInUser() throws Exception {
        var me = testUser();
        when(userRepository.getReferenceById(USER_ID)).thenReturn(me);
        stubGoal(goal("g1", "1000.0000", null, GoalStatus.ACTIVE));

        send(post("/api/v1/savings/goals"), "{\"name\":\"Car\",\"targetAmount\":\"1\"}").andExpect(status().isCreated());
        send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"1\"}").andExpect(status().isCreated());

        var goal = ArgumentCaptor.forClass(SavingsGoal.class);
        verify(goalRepo).save(goal.capture());
        assertThat(goal.getValue().getUser()).isSameAs(me);
        var contribution = ArgumentCaptor.forClass(GoalContribution.class);
        verify(contribRepo).save(contribution.capture());
        assertThat(contribution.getValue().getUser()).isSameAs(me);
    }

    @Test
    void createStoresScale4AndMapsEveryField() throws Exception {
        var r = send(post("/api/v1/savings/goals"), """
                {"name":" Car ","targetAmount":"12000","color":"#22AA88","icon":" car "}""");
        var saved = ArgumentCaptor.forClass(SavingsGoal.class);
        verify(goalRepo).save(saved.capture());
        r.andExpect(status().isCreated())
         .andExpect(jsonPath("$.data.name").value("Car"))
         .andExpect(jsonPath("$.data.targetAmount").value("12000.0000"))
         .andExpect(jsonPath("$.data.color").value("#22AA88"))
         .andExpect(jsonPath("$.data.icon").value("car"));
        assertThat(saved.getValue().getTargetAmount()).hasToString("12000.0000");
    }

    @Test
    void patchSetsDateColourAndScale4() throws Exception {
        SavingsGoal g = goal("g1", "1000.0000", null, GoalStatus.ACTIVE);
        stubGoal(g);
        send(patch("/api/v1/savings/goals/g1"), """
                {"targetAmount":"2500","targetDate":"2031-03-01","color":"#ABCDEF"}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targetAmount").value("2500.0000"))
            .andExpect(jsonPath("$.data.targetDate").value("2031-03-01"))
            .andExpect(jsonPath("$.data.color").value("#ABCDEF"));
        assertThat(g.getTargetAmount()).hasToString("2500.0000");
    }

    @Test
    void amountPrecisionAndPatchRulesAreValidated() throws Exception {
        stubGoal(goal("g1", "1000.0000", null, GoalStatus.ACTIVE));
        expectFieldError(send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"1.00001\"}"),
                "amount", "Enter a valid amount with up to 4 decimal places");
        expectFieldError(send(patch("/api/v1/savings/goals/g1"), "{\"targetAmount\":\"1.00001\"}"),
                "targetAmount", "Enter a valid amount with up to 4 decimal places");
        expectFieldError(send(patch("/api/v1/savings/goals/g1"), "{\"targetAmount\":\"0\"}"),
                "targetAmount", "Amount must be greater than zero");
        expectFieldError(send(patch("/api/v1/savings/goals/g1"), "{\"color\":\"blue\"}"), "color", "Enter a colour as #RRGGBB");
        expectFieldError(send(patch("/api/v1/savings/goals/g1"), "{\"name\":\"" + "x".repeat(61) + "\"}"),
                "name", "Keep this under 60 characters");
        send(post("/api/v1/savings/goals/g1/contributions"), "{\"amount\":\"1\",\"notes\":\"  bonus  \"}")
            .andExpect(jsonPath("$.data.notes").value("bonus"));
    }

    @Test
    void bestAndWorstKeepTheFirstOfTiedMonthsAndAverageRoundsHalfUp() throws Exception {
        when(txRepo.sumIncomeAndExpense(any(), any(), any())).thenReturn(row("0.0000", "0.0000"));
        when(txRepo.monthlyTrend(USER_ID, thisMonth.minusMonths(11).atDay(1), thisMonth.atEndOfMonth())).thenReturn(List.of(
                month("2026-05", "100.0000", "0.0000"), month("2026-06", "100.0000", "0.0000"),
                month("2026-07", "0.0000", "39.9850"), month("2026-08", "0.0000", "39.9850")));
        mvc.perform(get("/api/v1/savings").with(asUser()))
           .andExpect(jsonPath("$.data.best.month").value("2026-05"))
           .andExpect(jsonPath("$.data.worst.month").value("2026-07"))
           .andExpect(jsonPath("$.data.averageMonthly").value("30.01")); // 120.03 / 4 = 30.0075 → HALF_UP
    }
}
