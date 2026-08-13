package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.repository.*;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Dashboard + Analytics + Budget + Savings + Recurring + NetWorth + Settings + ExchangeRate
 * controllers — lighter services implemented inline for brevity.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final TransactionRepository txRepo;
    private final UserSettingsRepository settingsRepo;
    private final FinancialAccountRepository accountRepo;
    private final ExchangeRateRepository rateRepo;
    private final CategoryRepository categoryRepo;

    public DashboardController(TransactionRepository txRepo,
                               UserSettingsRepository settingsRepo,
                               FinancialAccountRepository accountRepo,
                               ExchangeRateRepository rateRepo,
                               CategoryRepository categoryRepo) {
        this.txRepo = txRepo;
        this.settingsRepo = settingsRepo;
        this.accountRepo = accountRepo;
        this.rateRepo = rateRepo;
        this.categoryRepo = categoryRepo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> dashboard(
            @RequestParam(defaultValue = "this-month") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String userId = SecurityUtils.currentUserId();
        var settings = settingsRepo.findById(userId).orElseThrow();
        LocalDate[] range = resolvePeriod(period, from, to, settings.getTimezone());

        List<Object[]> sumsList = txRepo.sumIncomeAndExpense(userId, range[0], range[1]);
        Object[] sums = sumsList.isEmpty() ? new Object[]{0, 0} : sumsList.get(0);
        BigDecimal income = new BigDecimal(sums[0].toString());
        BigDecimal expense = new BigDecimal(sums[1].toString());
        BigDecimal savings = income.subtract(expense);

        Double savingsRate = null;
        if (income.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = savings.divide(income, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }

        List<Object[]> lifetimeList = txRepo.sumIncomeAndExpense(userId, LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31));
        Object[] lifetimeSums = lifetimeList.isEmpty() ? new Object[]{0, 0} : lifetimeList.get(0);
        BigDecimal lifetimeIncome = new BigDecimal(lifetimeSums[0].toString());
        BigDecimal lifetimeExpense = new BigDecimal(lifetimeSums[1].toString());
        BigDecimal lifetimeSavings = lifetimeIncome.subtract(lifetimeExpense);

        String baseCurrency = settings.getBaseCurrency();
        List<FinancialAccount> accounts = accountRepo.findActiveByUserId(userId);
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        List<Map<String, Object>> unconvertedAccounts = new ArrayList<>();

        for (FinancialAccount a : accounts) {
            BigDecimal movement = txRepo.computeAccountMovement(a.getId());
            BigDecimal balance = a.getOpeningBalance().add(movement != null ? movement : BigDecimal.ZERO);

            BigDecimal baseBalance;
            if (a.getCurrency().equals(baseCurrency)) {
                baseBalance = balance;
            } else {
                Optional<ExchangeRate> rateOpt = rateRepo.findEffectiveRate(userId, a.getCurrency(), baseCurrency, LocalDate.now());
                if (rateOpt.isPresent()) {
                    baseBalance = balance.multiply(rateOpt.get().getRate()).setScale(4, RoundingMode.HALF_UP);
                } else {
                    unconvertedAccounts.add(Map.of(
                            "accountId", a.getId(),
                            "name", a.getName(),
                            "currency", a.getCurrency(),
                            "balance", balance.toPlainString()
                    ));
                    continue;
                }
            }

            switch (a.getAccountClass()) {
                case ASSET -> totalAssets = totalAssets.add(baseBalance);
                case LIABILITY -> totalLiabilities = totalLiabilities.add(baseBalance);
            }
        }

        BigDecimal netWorth = totalAssets.add(totalLiabilities);

        List<Object[]> rawExpenseByCategory = txRepo.sumByCategory(userId, TransactionType.EXPENSE, range[0], range[1]);
        List<Category> userCategories = categoryRepo.findActiveByUserId(userId);
        Map<String, Category> catMap = new HashMap<>();
        for (Category c : userCategories) catMap.put(c.getId(), c);

        List<Map<String, Object>> spendingByCategory = new ArrayList<>();
        BigDecimal totalCategorizedExpense = BigDecimal.ZERO;

        for (Object[] r : rawExpenseByCategory) {
            totalCategorizedExpense = totalCategorizedExpense.add(new BigDecimal(r[1].toString()));
        }

        for (Object[] r : rawExpenseByCategory) {
            String catId = (String) r[0];
            BigDecimal amount = new BigDecimal(r[1].toString());
            Category c = catId != null ? catMap.get(catId) : null;

            double share = 0.0;
            if (totalCategorizedExpense.compareTo(BigDecimal.ZERO) > 0) {
                share = amount.divide(totalCategorizedExpense, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
            }

            Map<String, Object> catData = new HashMap<>();
            catData.put("categoryId", catId);
            catData.put("categoryName", c != null ? c.getName() : "Uncategorized");
            catData.put("color", c != null ? c.getColor() : null);
            catData.put("icon", c != null ? c.getIcon() : null);
            catData.put("total", amount.toPlainString());
            catData.put("share", share);
            spendingByCategory.add(catData);
        }

        spendingByCategory.sort((m1, m2) -> new BigDecimal((String) m2.get("total")).compareTo(new BigDecimal((String) m1.get("total"))));

        var recent = txRepo.findFiltered(userId, null, null, null, range[0], range[1], PageRequest.of(0, 5));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseCurrency", baseCurrency);
        data.put("period", Map.of("preset", period, "from", range[0].toString(), "to", range[1].toString()));
        data.put("totalBalance", netWorth.toPlainString());
        data.put("netWorth", netWorth.toPlainString());
        data.put("assets", totalAssets.toPlainString());
        data.put("liabilities", totalLiabilities.toPlainString());
        data.put("lifetimeSavings", lifetimeSavings.toPlainString());
        data.put("income", income.toPlainString());
        data.put("expenses", expense.toPlainString());
        data.put("savings", savings.toPlainString());
        data.put("savingsRate", savingsRate);
        data.put("spendingByCategory", spendingByCategory);
        data.put("unconvertedAccounts", unconvertedAccounts);

        data.put("recentTransactions", recent.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("type", t.getType().name());
            m.put("amount", t.getAmount().toPlainString());
            m.put("currency", t.getCurrency());
            m.put("baseAmount", t.getBaseAmount().toPlainString());
            m.put("categoryId", t.getCategory() != null ? t.getCategory().getId() : null);
            m.put("categoryName", t.getCategory() != null ? t.getCategory().getName() : null);
            m.put("accountName", t.getAccount() != null ? t.getAccount().getName() : null);
            m.put("merchant", t.getMerchant());
            m.put("occurredOn", t.getOccurredOn().toString());
            return m;
        }).toList());
        return ResponseEntity.ok(new ApiEnvelope.Success<>(data));
    }

    static LocalDate[] resolvePeriod(String period, String from, String to, String timezone) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "this-month" -> new LocalDate[]{today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth())};
            case "last-month" -> {
                YearMonth prev = YearMonth.from(today).minusMonths(1);
                yield new LocalDate[]{prev.atDay(1), prev.atEndOfMonth()};
            }
            case "last-7-days" -> new LocalDate[]{today.minusDays(6), today};
            case "last-30-days" -> new LocalDate[]{today.minusDays(29), today};
            case "this-year" -> new LocalDate[]{today.withDayOfYear(1), LocalDate.of(today.getYear(), 12, 31)};
            case "last-year" -> new LocalDate[]{LocalDate.of(today.getYear() - 1, 1, 1), LocalDate.of(today.getYear() - 1, 12, 31)};
            case "custom" -> new LocalDate[]{LocalDate.parse(from != null ? from : today.toString()), LocalDate.parse(to != null ? to : today.toString())};
            default -> new LocalDate[]{today.withDayOfMonth(1), today};
        };
    }
}
