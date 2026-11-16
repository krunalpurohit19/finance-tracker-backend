package com.financetracker.api.controller;

import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.RecurringTransaction;
import com.financetracker.api.entity.Transaction;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.entity.enums.Frequency;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.RecurringTransactionRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.service.RecurringService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
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
 * Pins the JSON contract of /api/v1/recurring and the generation engine's current behaviour.
 * Bodies are compared STRICTLY; generated transactions are inspected field by field.
 */
@WebMvcTest(RecurringController.class)
@Import(RecurringService.class)
class RecurringApiContractTest extends WebSliceTest {

    @MockitoBean RecurringTransactionRepository recurringRepo;
    @MockitoBean FinancialAccountRepository accountRepo;
    @MockitoBean CategoryRepository categoryRepo;
    @MockitoBean TransactionRepository txRepo;
    @MockitoBean ExchangeRateRepository rateRepo;
    @MockitoBean UserSettingsRepository settingsRepo;

    LocalDate today;
    FinancialAccount checking;
    FinancialAccount savings;
    Category rent;

    @BeforeEach
    void setUp() {
        today = today();
        checking = FinancialAccount.builder().id("a-chk").name("Checking").currency("INR").build();
        savings = FinancialAccount.builder().id("a-sav").name("Savings").currency("INR").build();
        rent = Category.builder().id("c-rent").name("Rent").kind(CategoryKind.EXPENSE).build();
        when(accountRepo.findByIdAndUserIdAndDeletedAtIsNull("a-chk", USER_ID)).thenReturn(Optional.of(checking));
        when(accountRepo.findByIdAndUserIdAndDeletedAtIsNull("a-sav", USER_ID)).thenReturn(Optional.of(savings));
        when(categoryRepo.findByIdAndUserIdAndDeletedAtIsNull("c-rent", USER_ID)).thenReturn(Optional.of(rent));
        when(settingsRepo.findById(USER_ID)).thenReturn(Optional.of(UserSettings.builder().userId(USER_ID).build()));
        when(recurringRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.findByRecurringIdAndOccurrenceOn(anyString(), any())).thenReturn(Optional.empty());
    }

    /** The date the engine considers "today". */
    LocalDate today() {
        return LocalDate.now();
    }

    private ResultActions send(MockHttpServletRequestBuilder req, String json) throws Exception {
        return mvc.perform(req.with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private RecurringTransaction rule(String id, Frequency frequency, LocalDate next) {
        return RecurringTransaction.builder().id(id).name("Rule " + id).type(TransactionType.EXPENSE)
                .amount(new BigDecimal("100.0000")).currency("INR").account(checking).frequency(frequency)
                .startOn(next).nextOccurrence(next).build();
    }

    private List<Transaction> generated(int count) {
        var captor = ArgumentCaptor.forClass(Transaction.class);
        verify(txRepo, times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    // ── Rules: list / upcoming / create / patch / delete ────────────────

    @Test
    void listReturnsRules() throws Exception {
        RecurringTransaction monthly = rule("r1", Frequency.MONTHLY, LocalDate.of(2026, 10, 1));
        monthly.setCategory(rent);
        monthly.setMerchant("Landlord");
        monthly.setDayOfMonth(1);
        monthly.setEndOn(LocalDate.of(2027, 9, 1));
        RecurringTransaction transfer = rule("r2", Frequency.WEEKLY, LocalDate.of(2026, 10, 3));
        transfer.setType(TransactionType.TRANSFER);
        transfer.setTransferAccount(savings);
        transfer.setInterval(2);
        transfer.setActive(false);
        when(recurringRepo.findByUserIdAndDeletedAtIsNullOrderByNextOccurrence(USER_ID)).thenReturn(List.of(monthly, transfer));

        mvc.perform(get("/api/v1/recurring").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("""
                {"ok":true,"data":[
                  {"id":"r1","name":"Rule r1","type":"EXPENSE","amount":"100.0000","currency":"INR","accountId":"a-chk",
                   "categoryId":"c-rent","merchant":"Landlord","frequency":"MONTHLY","interval":1,"startOn":"2026-10-01",
                   "endOn":"2027-09-01","dayOfMonth":1,"nextOccurrence":"2026-10-01","isActive":true,"autoPost":true},
                  {"id":"r2","name":"Rule r2","type":"TRANSFER","amount":"100.0000","currency":"INR","accountId":"a-chk",
                   "transferAccountId":"a-sav","frequency":"WEEKLY","interval":2,"startOn":"2026-10-03",
                   "nextOccurrence":"2026-10-03","isActive":false,"autoPost":true}]}""", JsonCompareMode.STRICT));
    }

    @Test
    void upcomingHasTheSameShape() throws Exception {
        when(recurringRepo.findUpcoming(USER_ID)).thenReturn(List.of(rule("r1", Frequency.DAILY, LocalDate.of(2026, 10, 1))));
        mvc.perform(get("/api/v1/recurring/upcoming").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("""
                {"ok":true,"data":[{"id":"r1","name":"Rule r1","type":"EXPENSE","amount":"100.0000","currency":"INR",
                  "accountId":"a-chk","frequency":"DAILY","interval":1,"startOn":"2026-10-01",
                  "nextOccurrence":"2026-10-01","isActive":true,"autoPost":true}]}""", JsonCompareMode.STRICT));
    }

    @Test
    void createReturns201() throws Exception {
        var r = send(post("/api/v1/recurring"), """
                {"name":"Rent","type":"EXPENSE","amount":"25000.0000","accountId":"a-chk","categoryId":"c-rent",
                 "frequency":"MONTHLY","interval":1,"startOn":"2026-10-01","dayOfMonth":1,"merchant":"Landlord"}""");
        var saved = ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(recurringRepo).save(saved.capture());
        r.andExpect(status().isCreated())
         .andExpect(content().json("""
                {"ok":true,"data":{"id":"%s","name":"Rent","type":"EXPENSE","amount":"25000.0000","currency":"INR",
                  "accountId":"a-chk","categoryId":"c-rent","merchant":"Landlord","frequency":"MONTHLY","interval":1,
                  "startOn":"2026-10-01","dayOfMonth":1,"nextOccurrence":"2026-10-01","isActive":true,"autoPost":true}}"""
                 .formatted(saved.getValue().getId()), JsonCompareMode.STRICT));
    }

    @Test
    void patchUpdatesAndNullEndOnClearsIt() throws Exception {
        RecurringTransaction r1 = rule("r1", Frequency.MONTHLY, LocalDate.of(2026, 10, 1));
        r1.setEndOn(LocalDate.of(2027, 1, 1));
        when(recurringRepo.findByIdAndUserIdAndDeletedAtIsNull("r1", USER_ID)).thenReturn(Optional.of(r1));

        send(patch("/api/v1/recurring/r1"), """
                {"name":"Rent (new flat)","amount":"27000.0000","isActive":false,"endOn":null}""")
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"ok":true,"data":{"id":"r1","name":"Rent (new flat)","type":"EXPENSE","amount":"27000.0000",
                  "currency":"INR","accountId":"a-chk","frequency":"MONTHLY","interval":1,"startOn":"2026-10-01",
                  "nextOccurrence":"2026-10-01","isActive":false,"autoPost":true}}""", JsonCompareMode.STRICT));
        assertThat(r1.getEndOn()).isNull();
    }

    @Test
    void patchUnknownRuleIs404() throws Exception {
        send(patch("/api/v1/recurring/nope"), "{\"name\":\"x\"}")
            .andExpect(status().isNotFound())
            .andExpect(content().json("""
                {"ok":false,"error":{"code":"NOT_FOUND","message":"Recurring transaction not found"}}""", JsonCompareMode.STRICT));
    }

    @Test
    void deleteSoftDeletes() throws Exception {
        RecurringTransaction r1 = rule("r1", Frequency.MONTHLY, LocalDate.of(2026, 10, 1));
        when(recurringRepo.findByIdAndUserIdAndDeletedAtIsNull("r1", USER_ID)).thenReturn(Optional.of(r1));
        mvc.perform(delete("/api/v1/recurring/r1").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json("{\"ok\":true,\"data\":{\"id\":\"r1\"}}", JsonCompareMode.STRICT));
        assertThat(r1.getDeletedAt()).isNotNull();
    }

    // ── POST /generate ──────────────────────────────────────────────────

    @Test
    void generateCatchesUpEveryMissedOccurrence() throws Exception {
        RecurringTransaction daily = rule("r1", Frequency.DAILY, today.minusDays(2));
        daily.setCategory(rent);
        daily.setMerchant("Cafe");
        when(recurringRepo.findDue(USER_ID, today)).thenReturn(List.of(daily));

        mvc.perform(post("/api/v1/recurring/generate").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.generated").value(3));

        List<Transaction> txs = generated(3);
        assertThat(txs).extracting(Transaction::getOccurredOn)
                .containsExactly(today.minusDays(2), today.minusDays(1), today);
        Transaction t = txs.get(0);
        assertThat(t.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(t.getAmount()).isEqualByComparingTo("100");
        assertThat(t.getCurrency()).isEqualTo("INR");
        assertThat(t.getBaseAmount()).isEqualByComparingTo("100");
        assertThat(t.getBaseCurrency()).isEqualTo("INR");
        assertThat(t.getFxRate()).isEqualByComparingTo("1");
        assertThat(t.getAccount()).isSameAs(checking);
        assertThat(t.getCategory()).isSameAs(rent);
        assertThat(t.getMerchant()).isEqualTo("Cafe");
        assertThat(t.getRecurring()).isSameAs(daily);
        assertThat(t.getOccurrenceOn()).isEqualTo(t.getOccurredOn());
        assertThat(daily.getNextOccurrence()).isEqualTo(today.plusDays(1));
        assertThat(daily.getLastGenerated()).isEqualTo(today);
        assertThat(daily.isActive()).isTrue();
    }

    @Test
    void generateSkipsOccurrencesThatAlreadyExist() throws Exception {
        RecurringTransaction daily = rule("r1", Frequency.DAILY, today.minusDays(1));
        when(recurringRepo.findDue(USER_ID, today)).thenReturn(List.of(daily));
        when(txRepo.findByRecurringIdAndOccurrenceOn("r1", today.minusDays(1))).thenReturn(Optional.of(new Transaction()));

        mvc.perform(post("/api/v1/recurring/generate").with(asUser())).andExpect(jsonPath("$.data.generated").value(1));
        assertThat(generated(1).get(0).getOccurredOn()).isEqualTo(today);
    }

    @Test
    void generateStopsAtEndOnAndDeactivates() throws Exception {
        RecurringTransaction daily = rule("r1", Frequency.DAILY, today.minusDays(2));
        daily.setEndOn(today.minusDays(1));
        when(recurringRepo.findDue(USER_ID, today)).thenReturn(List.of(daily));

        mvc.perform(post("/api/v1/recurring/generate").with(asUser())).andExpect(jsonPath("$.data.generated").value(2));
        assertThat(generated(2)).extracting(Transaction::getOccurredOn).containsExactly(today.minusDays(2), today.minusDays(1));
        assertThat(daily.isActive()).isFalse();
        assertThat(daily.getNextOccurrence()).isEqualTo(today);
    }

    @Test
    void generateConvertsForeignCurrencyAtTheRateInForce() throws Exception {
        RecurringTransaction usd = rule("r1", Frequency.MONTHLY, today);
        usd.setCurrency("USD");
        when(recurringRepo.findDue(USER_ID, today)).thenReturn(List.of(usd));
        when(rateRepo.findEffectiveRate(USER_ID, "USD", "INR", today))
                .thenReturn(Optional.of(ExchangeRate.builder().rate(new BigDecimal("83.25000000")).build()));

        mvc.perform(post("/api/v1/recurring/generate").with(asUser())).andExpect(jsonPath("$.data.generated").value(1));
        Transaction t = generated(1).get(0);
        assertThat(t.getFxRate()).isEqualByComparingTo("83.25");
        assertThat(t.getBaseAmount()).isEqualByComparingTo("8325");
        assertThat(t.getBaseCurrency()).isEqualTo("INR");
    }

    @Test
    void generateTransfersAsOneRow() throws Exception {
        RecurringTransaction transfer = rule("r1", Frequency.MONTHLY, today);
        transfer.setType(TransactionType.TRANSFER);
        transfer.setTransferAccount(savings);
        when(recurringRepo.findDue(USER_ID, today)).thenReturn(List.of(transfer));

        mvc.perform(post("/api/v1/recurring/generate").with(asUser())).andExpect(jsonPath("$.data.generated").value(1));
        Transaction t = generated(1).get(0);
        assertThat(t.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(t.getAccount()).isSameAs(checking);
        assertThat(t.getTransferAccount()).isSameAs(savings);
    }

    @Test
    void generateCreatesAtMost500PerCall() throws Exception {
        RecurringTransaction daily = rule("r1", Frequency.DAILY, today.minusDays(600));
        when(recurringRepo.findDue(USER_ID, today)).thenReturn(List.of(daily));

        mvc.perform(post("/api/v1/recurring/generate").with(asUser())).andExpect(jsonPath("$.data.generated").value(500));
        assertThat(daily.getNextOccurrence()).isEqualTo(today.minusDays(100));
    }

    @Test
    void generateWithNothingDue() throws Exception {
        mvc.perform(post("/api/v1/recurring/generate").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.generated").value(0));
    }

    // ── Input validation: 422 VALIDATION_FAILED with field errors (was 500) ──

    private void expectFieldError(ResultActions r, String field, String message) throws Exception {
        r.andExpect(status().is(422))
         .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
         .andExpect(jsonPath("$.error.fieldErrors['" + field + "'][0]").value(message));
    }

    static final String VALID = """
            {"name":"Rent","type":"EXPENSE","amount":"25000","accountId":"a-chk","frequency":"MONTHLY","startOn":"2026-10-01"
            """.strip();

    @Test
    void createValidatesEveryField() throws Exception {
        String url = "/api/v1/recurring";
        expectFieldError(send(post(url), VALID.replace("\"name\":\"Rent\"", "\"name\":\" \"") + "}"), "name", "Required");
        expectFieldError(send(post(url), VALID.replace("\"EXPENSE\"", "\"GIFT\"") + "}"), "type", "Invalid value");
        expectFieldError(send(post(url), VALID.replace("\"25000\"", "\"0\"") + "}"), "amount", "Amount must be greater than zero");
        expectFieldError(send(post(url), VALID.replace("\"25000\"", "\"1.00001\"") + "}"), "amount",
                "Enter a valid amount with up to 4 decimal places");
        expectFieldError(send(post(url), VALID.replace("\"MONTHLY\"", "\"HOURLY\"") + "}"), "frequency", "Invalid value");
        expectFieldError(send(post(url), VALID + ",\"interval\":0}"), "interval", "Between 1 and 365");
        expectFieldError(send(post(url), VALID + ",\"interval\":366}"), "interval", "Between 1 and 365");
        expectFieldError(send(post(url), VALID + ",\"dayOfMonth\":0}"), "dayOfMonth", "Between 1 and 31");
        expectFieldError(send(post(url), VALID + ",\"dayOfMonth\":32}"), "dayOfMonth", "Between 1 and 31");
        expectFieldError(send(post(url), VALID + ",\"currency\":\"rupees\"}"), "currency", "Enter a 3-letter currency code");
        expectFieldError(send(post(url), VALID + ",\"merchant\":\"" + "m".repeat(121) + "\"}"), "merchant", "Keep this under 120 characters");
        expectFieldError(send(post(url), VALID.replace(",\"startOn\":\"2026-10-01\"", "") + "}"), "startOn", "Required");
        expectFieldError(send(post(url), VALID.replace("\"accountId\":\"a-chk\",", "") + "}"), "accountId", "Choose an account");
        verify(recurringRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createWithUnknownAccountIs404() throws Exception {
        send(post("/api/v1/recurring"), VALID.replace("a-chk", "someone-elses") + "}")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.message").value("Account not found"));
    }

    @Test
    void patchValidatesAndLeavesAbsentFieldsAlone() throws Exception {
        RecurringTransaction r1 = rule("r1", Frequency.MONTHLY, LocalDate.of(2026, 10, 1));
        r1.setEndOn(LocalDate.of(2027, 1, 1));
        when(recurringRepo.findByIdAndUserIdAndDeletedAtIsNull("r1", USER_ID)).thenReturn(Optional.of(r1));

        expectFieldError(send(patch("/api/v1/recurring/r1"), "{\"amount\":\"-1\"}"), "amount", "Amount must be greater than zero");
        expectFieldError(send(patch("/api/v1/recurring/r1"), "{\"name\":\"\"}"), "name", "Required");
        send(patch("/api/v1/recurring/r1"), "{\"isActive\":false}").andExpect(jsonPath("$.data.isActive").value(false));
        assertThat(r1.getEndOn()).isEqualTo(LocalDate.of(2027, 1, 1)); // absent ≠ null
        assertThat(r1.getName()).isEqualTo("Rule r1");
    }
}
