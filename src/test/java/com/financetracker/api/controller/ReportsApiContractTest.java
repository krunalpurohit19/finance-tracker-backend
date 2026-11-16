package com.financetracker.api.controller;

import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.Transaction;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.AccountClass;
import com.financetracker.api.entity.enums.AccountType;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.support.WebSliceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.financetracker.api.service.ReportService;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the JSON contract of the three read-only reports: /dashboard, /analytics and /net-worth.
 * Bodies are compared STRICTLY. A fixed custom period keeps "today" out of the expected JSON.
 */
@WebMvcTest(ReportController.class)
@Import(ReportService.class)
class ReportsApiContractTest extends WebSliceTest {

    static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    static final LocalDate TO = LocalDate.of(2026, 3, 31);
    static final String PERIOD = "?preset=custom&from=2026-03-01&to=2026-03-31";

    @MockitoBean TransactionRepository txRepo;
    @MockitoBean UserSettingsRepository settingsRepo;
    @MockitoBean FinancialAccountRepository accountRepo;
    @MockitoBean ExchangeRateRepository rateRepo;
    @MockitoBean CategoryRepository categoryRepo;

    FinancialAccount bank, card, dollars, euros;
    Category food, rent;

    @BeforeEach
    void setUp() {
        when(settingsRepo.findById(USER_ID)).thenReturn(Optional.of(UserSettings.builder().userId(USER_ID).build()));

        bank = account("a-bank", "Bank", AccountType.BANK, AccountClass.ASSET, "INR", "1000.0000", "500.0000");
        card = account("a-card", "Card", AccountType.CREDIT_CARD, AccountClass.LIABILITY, "INR", "0.0000", "-300.0000");
        dollars = account("a-usd", "Dollars", AccountType.WALLET, AccountClass.ASSET, "USD", "10.0000", "0.0000");
        euros = account("a-eur", "Euros", AccountType.CASH, AccountClass.ASSET, "EUR", "5.0000", "0.0000");
        when(accountRepo.findActiveByUserId(USER_ID)).thenReturn(List.of(bank, card, dollars, euros));
        when(rateRepo.findEffectiveRate(eq(USER_ID), eq("USD"), eq("INR"), any()))
                .thenReturn(Optional.of(ExchangeRate.builder().rate(new BigDecimal("80.00000000")).build()));
        when(rateRepo.findEffectiveRate(eq(USER_ID), eq("EUR"), eq("INR"), any())).thenReturn(Optional.empty());

        food = Category.builder().id("c-food").name("Food").kind(CategoryKind.EXPENSE).color("#ff0000").icon("restaurant").build();
        rent = Category.builder().id("c-rent").name("Rent").kind(CategoryKind.EXPENSE).build();
        when(categoryRepo.findActiveByUserId(USER_ID)).thenReturn(List.of(food, rent));

        when(txRepo.sumIncomeAndExpense(USER_ID, FROM, TO)).thenReturn(rows(row(dec("5000.0000"), dec("2000.0000"))));
        when(txRepo.sumByCategory(USER_ID, TransactionType.EXPENSE, FROM, TO)).thenReturn(
                rows(row("c-food", dec("500.0000")), row("c-rent", dec("1200.0000")), row(null, dec("300.0000"))));
    }

    @Test
    void dashboard() throws Exception {
        // lifetime: everything outside the March period
        when(txRepo.sumIncomeAndExpense(USER_ID, LocalDate.of(1000, 1, 1), LocalDate.of(9999, 12, 31)))
                .thenReturn(rows(row(dec("90000.0000"), dec("60000.5000"))));
        Transaction t = Transaction.builder().id("t1").type(TransactionType.EXPENSE).amount(dec("500.0000"))
                .currency("INR").baseAmount(dec("500.0000")).account(bank).category(food).merchant("Cafe")
                .occurredOn(LocalDate.of(2026, 3, 9)).build();
        Transaction transfer = Transaction.builder().id("t2").type(TransactionType.TRANSFER).amount(dec("100.0000"))
                .currency("INR").baseAmount(dec("100.0000")).account(bank).transferAccount(card)
                .occurredOn(LocalDate.of(2026, 3, 2)).build();
        when(txRepo.findFiltered(eq(USER_ID), any(), any(), any(), eq(FROM), eq(TO), any())).thenReturn(List.of(t, transfer));

        mvc.perform(get("/api/v1/dashboard" + PERIOD).with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json(DASHBOARD, JsonCompareMode.STRICT));
    }

    @Test
    void analytics() throws Exception {
        LocalDate prevFrom = LocalDate.of(2026, 1, 29), prevTo = LocalDate.of(2026, 2, 28); // the 31 days before
        when(txRepo.sumIncomeAndExpense(USER_ID, prevFrom, prevTo)).thenReturn(rows(row(dec("4000.0000"), dec("1000.0000"))));
        when(txRepo.sumByCategory(USER_ID, TransactionType.EXPENSE, prevFrom, prevTo))
                .thenReturn(rows(row("c-food", dec("1000.0000"))));
        when(txRepo.monthlyTrend(USER_ID, FROM, TO)).thenReturn(rows(row("2026-03", dec("5000.0000"), dec("2000.0000"))));
        when(txRepo.topMerchants(eq(USER_ID), eq(FROM), eq(TO), any())).thenReturn(rows(row("Cafe", dec("500.0000"), 3L)));
        when(txRepo.sumByAccount(USER_ID, FROM, TO)).thenReturn(
                rows(row("a-bank", dec("5000.0000"), dec("1700.0000"), 9L), row("a-gone", dec("0.0000"), dec("300.0000"), 1L)));

        mvc.perform(get("/api/v1/analytics" + PERIOD).with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json(ANALYTICS, JsonCompareMode.STRICT));
    }

    @Test
    void netWorth() throws Exception {
        when(txRepo.monthlyTrend(eq(USER_ID), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/net-worth?months=2").with(asUser()))
           .andExpect(status().isOk())
           .andExpect(content().json(NET_WORTH.replace("{today}", LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString())
                   .replace("{m0}", java.time.YearMonth.now(java.time.ZoneId.of("Asia/Kolkata")).toString())
                   .replace("{m1}", java.time.YearMonth.now(java.time.ZoneId.of("Asia/Kolkata")).minusMonths(1).toString()), JsonCompareMode.STRICT));
    }

    @Test
    void legacyPeriodParamStillWorksAndBadPeriodsAre422() throws Exception {
        when(txRepo.findFiltered(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/dashboard?period=custom&from=2026-03-01&to=2026-03-31").with(asUser()))
           .andExpect(status().isOk()).andExpect(jsonPath("$.data.income").value("5000.0000"));
        mvc.perform(get("/api/v1/dashboard?preset=next-decade").with(asUser()))
           .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.preset").exists());
        mvc.perform(get("/api/v1/analytics?preset=custom&from=2026-03-01").with(asUser()))
           .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.from").exists());
        mvc.perform(get("/api/v1/analytics?preset=custom&from=2026-04-01&to=2026-03-01").with(asUser()))
           .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.to").exists());
        mvc.perform(get("/api/v1/dashboard?preset=custom&from=yesterday&to=2026-03-01").with(asUser()))
           .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.from").exists());
        mvc.perform(get("/api/v1/net-worth?months=100000").with(asUser()))
           .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.months").exists());
    }

    @Test
    void emptyAccountReportsZeroesAndAMonthWithoutIncomeHasNoRate() throws Exception {
        when(accountRepo.findActiveByUserId(USER_ID)).thenReturn(List.of());
        when(txRepo.sumIncomeAndExpense(any(), any(), any())).thenReturn(List.of());
        when(txRepo.sumByCategory(any(), any(), any(), any())).thenReturn(List.of());
        when(txRepo.findFiltered(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(txRepo.monthlyTrend(USER_ID, FROM, TO)).thenReturn(rows(row("2026-03", dec("0.0000"), dec("40.0000"))));
        mvc.perform(get("/api/v1/dashboard" + PERIOD).with(asUser()))
           .andExpect(jsonPath("$.data.netWorth").value("0.0000")).andExpect(jsonPath("$.data.income").value("0.0000"))
           .andExpect(jsonPath("$.data.savingsRate").doesNotExist());
        mvc.perform(get("/api/v1/analytics" + PERIOD).with(asUser()))
           .andExpect(status().isOk()) // was a 500: Map.of() rejects the null rate
           .andExpect(jsonPath("$.data.monthly[0].net").value("-40.0000"))
           .andExpect(jsonPath("$.data.monthly[0].savingsRate").doesNotExist());
    }

    @Test
    void archivedAccountsAreFlaggedInNetWorth() throws Exception {
        bank.setArchivedAt(java.time.Instant.EPOCH);
        when(txRepo.monthlyTrend(eq(USER_ID), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/net-worth?months=1").with(asUser()))
           .andExpect(jsonPath("$.data.assetAccounts[0].archived").value(true))
           .andExpect(jsonPath("$.data.assetAccounts[1].archived").value(false));
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private FinancialAccount account(String id, String name, AccountType type, AccountClass cls, String currency,
                                     String opening, String movement) {
        when(txRepo.computeAccountMovement(id)).thenReturn(dec(movement));
        return FinancialAccount.builder().id(id).name(name).type(type).accountClass(cls).currency(currency)
                .openingBalance(dec(opening)).build();
    }

    private static BigDecimal dec(String s) {
        return new BigDecimal(s);
    }

    private static Object[] row(Object... cols) {
        return cols;
    }

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }

    static final String DASHBOARD = """
            {"ok":true,"data":{"baseCurrency":"INR","period":{"preset":"custom","to":"2026-03-31","from":"2026-03-01"},
              "totalBalance":"2000.0000","netWorth":"2000.0000","assets":"2300.0000","liabilities":"-300.0000",
              "lifetimeSavings":"29999.5000","income":"5000.0000","expenses":"2000.0000","savings":"3000.0000","savingsRate":60.0,
              "spendingByCategory":[
                {"total":"1200.0000","share":60.0,"categoryName":"Rent","categoryId":"c-rent"},
                {"total":"500.0000","color":"#ff0000","icon":"restaurant","share":25.0,"categoryName":"Food","categoryId":"c-food"},
                {"total":"300.0000","share":15.0,"categoryName":"Uncategorized"}],
              "unconvertedAccounts":[{"currency":"EUR","accountId":"a-eur","balance":"5.0000","name":"Euros"}],
              "recentTransactions":[
                {"id":"t1","type":"EXPENSE","amount":"500.0000","currency":"INR","baseAmount":"500.0000","categoryId":"c-food",
                 "categoryName":"Food","accountName":"Bank","merchant":"Cafe","occurredOn":"2026-03-09"},
                {"id":"t2","type":"TRANSFER","amount":"100.0000","currency":"INR","baseAmount":"100.0000","accountName":"Bank",
                 "occurredOn":"2026-03-02"}]}}""";
    static final String ANALYTICS = """
            {"ok":true,"data":{"baseCurrency":"INR","period":{"preset":"custom","to":"2026-03-31","from":"2026-03-01"},
              "previousPeriod":{"to":"2026-02-28","from":"2026-01-29"},
              "totals":{"income":"5000.0000","savingsRate":60.0,"net":"3000.0000","expenses":"2000.0000"},
              "monthly":[{"savingsRate":60.0,"month":"2026-03","income":"5000.0000","net":"3000.0000","expenses":"2000.0000"}],
              "byCategory":[
                {"total":"1200.0000","share":60.0,"categoryName":"Rent","categoryId":"c-rent"},
                {"total":"500.0000","color":"#ff0000","share":25.0,"categoryName":"Food","categoryId":"c-food"},
                {"total":"300.0000","share":15.0,"categoryName":"Uncategorized"}],
              "categoryChange":[
                {"current":"1200.0000","previous":"0.0000","change":"1200.0000","changePercent":100.0,"categoryName":"Rent","categoryId":"c-rent"},
                {"current":"300.0000","previous":"0.0000","change":"300.0000","changePercent":100.0,"categoryName":"Uncategorized"},
                {"current":"500.0000","color":"#ff0000","previous":"1000.0000","change":"-500.0000","changePercent":-50.0,
                 "categoryName":"Food","categoryId":"c-food"}],
              "topMerchants":[{"count":3,"merchant":"Cafe","total":"500.0000"}],
              "byAccount":[{"name":"Bank","transactionCount":9,"income":"5000.0000","accountId":"a-bank","currency":"INR",
                            "expenses":"1700.0000"}]}}""";
    static final String NET_WORTH = """
            {"ok":true,"data":{"baseCurrency":"INR","asOf":"{today}","netWorth":"2000.0000","assets":"2300.0000",
              "liabilities":"-300.0000","monthChange":"0.0000","monthChangePercent":0.0,
              "history":[
                {"assets":"2000.0000","month":"{m1}","netWorth":"2000.0000","liabilities":"0.0000"},
                {"assets":"2000.0000","month":"{m0}","netWorth":"2000.0000","change":"0.0000","liabilities":"0.0000"}],
              "assetAccounts":[
                {"accountId":"a-bank","archived":false,"balance":"1500.0000","name":"Bank","convertible":true,"currency":"INR",
                 "share":65.22,"type":"BANK","baseBalance":"1500.0000"},
                {"accountId":"a-usd","archived":false,"balance":"10.0000","name":"Dollars","convertible":true,"currency":"USD",
                 "share":34.78,"type":"WALLET","baseBalance":"800.0000"},
                {"accountId":"a-eur","archived":false,"balance":"5.0000","name":"Euros","convertible":false,"currency":"EUR",
                 "share":0.0,"type":"CASH","baseBalance":"0.0000"}],
              "liabilityAccounts":[
                {"accountId":"a-card","archived":false,"balance":"-300.0000","name":"Card","convertible":true,"currency":"INR",
                 "share":100.0,"type":"CREDIT_CARD","baseBalance":"-300.0000"}],
              "unconvertedAccounts":[{"currency":"EUR","accountId":"a-eur","balance":"5.0000","name":"Euros"}]}}""";
}
