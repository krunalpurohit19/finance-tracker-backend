package com.financetracker.api.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.financetracker.api.dto.DecimalString;
import com.financetracker.api.dto.transaction.TransactionDtos.TransactionResponse;
import com.financetracker.api.entity.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Responses of the read-only reports: /dashboard, /analytics and /net-worth. Money is a JSON string,
 * ratios (share, savingsRate, changePercent) are JSON numbers, and null fields are omitted.
 */
public final class ReportDtos {

    private ReportDtos() {}

    public record Period(String preset, LocalDate from, LocalDate to) {}

    public record DateRange(LocalDate from, LocalDate to) {}

    /** An account in a currency with no exchange rate to the base currency: left out of the totals. */
    public record UnconvertedAccount(String accountId, String name, String currency, @DecimalString BigDecimal balance) {}

    public record CategorySpend(String categoryId, String categoryName, String color, String icon,
                                @DecimalString BigDecimal total, BigDecimal share) {}

    // ── dashboard ────────────────────────────────────────────────────────

    public record DashboardResponse(String baseCurrency, Period period, @DecimalString BigDecimal totalBalance,
                                    @DecimalString BigDecimal netWorth, @DecimalString BigDecimal assets,
                                    @DecimalString BigDecimal liabilities, @DecimalString BigDecimal lifetimeSavings,
                                    @DecimalString BigDecimal income, @DecimalString BigDecimal expenses,
                                    @DecimalString BigDecimal savings, BigDecimal savingsRate,
                                    List<CategorySpend> spendingByCategory,
                                    List<UnconvertedAccount> unconvertedAccounts,
                                    List<TransactionResponse> recentTransactions) {}

    // ── analytics ────────────────────────────────────────────────────────

    public record Totals(@DecimalString BigDecimal income, @DecimalString BigDecimal expenses,
                         @DecimalString BigDecimal net, BigDecimal savingsRate) {}

    public record MonthPoint(String month, @DecimalString BigDecimal income, @DecimalString BigDecimal expenses,
                             @DecimalString BigDecimal net, BigDecimal savingsRate) {}

    public record CategoryChange(String categoryId, String categoryName, String color,
                                 @DecimalString BigDecimal current, @DecimalString BigDecimal previous,
                                 @DecimalString BigDecimal change,
                                 @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal changePercent) {}

    public record MerchantSpend(String merchant, @DecimalString BigDecimal total, int count) {}

    /** Income and expenses are in the base currency, and {@code currency} says so (the app formats with it). */
    public record AccountActivity(String accountId, String name, String currency, @DecimalString BigDecimal income,
                                  @DecimalString BigDecimal expenses, int transactionCount) {}

    public record AnalyticsResponse(String baseCurrency, Period period, DateRange previousPeriod, Totals totals,
                                    List<MonthPoint> monthly, List<CategorySpend> byCategory,
                                    List<CategoryChange> categoryChange, List<MerchantSpend> topMerchants,
                                    List<AccountActivity> byAccount) {}

    // ── net worth ────────────────────────────────────────────────────────

    public record NetWorthPoint(String month, @DecimalString BigDecimal assets, @DecimalString BigDecimal liabilities,
                                @DecimalString BigDecimal netWorth, @DecimalString BigDecimal change) {}

    public record NetWorthAccount(String accountId, String name, AccountType type, String currency, boolean archived,
                                  @DecimalString BigDecimal balance, @DecimalString BigDecimal baseBalance,
                                  boolean convertible, BigDecimal share) {}

    public record NetWorthResponse(String baseCurrency, LocalDate asOf, @DecimalString BigDecimal netWorth,
                                   @DecimalString BigDecimal assets, @DecimalString BigDecimal liabilities,
                                   @DecimalString BigDecimal monthChange, BigDecimal monthChangePercent,
                                   List<NetWorthPoint> history, List<NetWorthAccount> assetAccounts,
                                   List<NetWorthAccount> liabilityAccounts,
                                   List<UnconvertedAccount> unconvertedAccounts) {}
}
