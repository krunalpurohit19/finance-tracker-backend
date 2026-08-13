package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final TransactionRepository txRepo;
    private final UserSettingsRepository settingsRepo;
    private final CategoryRepository categoryRepo;
    private final FinancialAccountRepository accountRepo;

    public AnalyticsController(TransactionRepository txRepo, UserSettingsRepository settingsRepo,
                               CategoryRepository categoryRepo, FinancialAccountRepository accountRepo) {
        this.txRepo = txRepo;
        this.settingsRepo = settingsRepo;
        this.categoryRepo = categoryRepo;
        this.accountRepo = accountRepo;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> analytics(
            @RequestParam(defaultValue = "this-month") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String userId = SecurityUtils.currentUserId();
        var settings = settingsRepo.findById(userId).orElseThrow();
        LocalDate[] range = DashboardController.resolvePeriod(period, from, to, settings.getTimezone());

        LocalDate prevFrom, prevTo;
        if (period.equals("this-month") || period.equals("last-month")) {
            prevFrom = range[0].minusMonths(1);
            prevTo = range[1].minusMonths(1);
            prevTo = prevTo.withDayOfMonth(prevTo.lengthOfMonth());
        } else if (period.equals("this-year") || period.equals("last-year")) {
            prevFrom = range[0].minusYears(1);
            prevTo = range[1].minusYears(1);
        } else {
            long days = ChronoUnit.DAYS.between(range[0], range[1]) + 1;
            prevFrom = range[0].minusDays(days);
            prevTo = range[1].minusDays(days);
        }

        List<Object[]> sumsList = txRepo.sumIncomeAndExpense(userId, range[0], range[1]);
        Object[] sums = sumsList.isEmpty() ? new Object[]{0, 0} : sumsList.get(0);
        BigDecimal totalIncome = new BigDecimal(sums[0].toString());
        BigDecimal totalExpense = new BigDecimal(sums[1].toString());
        BigDecimal net = totalIncome.subtract(totalExpense);
        
        Double savingsRate = null;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = net.divide(totalIncome, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }

        List<Object[]> prevSumsList = txRepo.sumIncomeAndExpense(userId, prevFrom, prevTo);
        Object[] prevSums = prevSumsList.isEmpty() ? new Object[]{0, 0} : prevSumsList.get(0);
        BigDecimal prevTotalExpense = new BigDecimal(prevSums[1].toString());

        List<Object[]> rawExpenseByCategory = txRepo.sumByCategory(userId, TransactionType.EXPENSE, range[0], range[1]);
        List<Object[]> rawPrevExpenseByCategory = txRepo.sumByCategory(userId, TransactionType.EXPENSE, prevFrom, prevTo);
        
        List<Category> userCategories = categoryRepo.findActiveByUserId(userId);
        Map<String, Category> catMap = new HashMap<>();
        for (Category c : userCategories) catMap.put(c.getId(), c);

        List<Map<String, Object>> byCategory = new ArrayList<>();
        List<Map<String, Object>> categoryChange = new ArrayList<>();
        
        Map<String, BigDecimal> prevCatMap = new HashMap<>();
        for (Object[] r : rawPrevExpenseByCategory) {
            prevCatMap.put((String) r[0], new BigDecimal(r[1].toString()));
        }

        for (Object[] r : rawExpenseByCategory) {
            String catId = (String) r[0];
            BigDecimal amount = new BigDecimal(r[1].toString());
            Category c = catId != null ? catMap.get(catId) : null;
            
            double share = 0.0;
            if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                share = amount.divide(totalExpense, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
            }

            Map<String, Object> catData = new HashMap<>();
            catData.put("categoryId", catId);
            catData.put("categoryName", c != null ? c.getName() : "Uncategorized");
            catData.put("color", c != null ? c.getColor() : null);
            catData.put("total", amount.toPlainString());
            catData.put("share", share);
            byCategory.add(catData);
            
            BigDecimal prevAmount = prevCatMap.getOrDefault(catId, BigDecimal.ZERO);
            BigDecimal change = amount.subtract(prevAmount);
            Double changePercent = null;
            if (prevAmount.compareTo(BigDecimal.ZERO) > 0) {
                changePercent = change.divide(prevAmount, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
            } else if (amount.compareTo(BigDecimal.ZERO) > 0) {
                changePercent = 100.0;
            }
            
            Map<String, Object> changeData = new HashMap<>();
            changeData.put("categoryId", catId);
            changeData.put("categoryName", c != null ? c.getName() : "Uncategorized");
            changeData.put("color", c != null ? c.getColor() : null);
            changeData.put("current", amount.toPlainString());
            changeData.put("previous", prevAmount.toPlainString());
            changeData.put("change", change.toPlainString());
            changeData.put("changePercent", changePercent);
            categoryChange.add(changeData);
        }
        
        byCategory.sort((m1, m2) -> new BigDecimal((String) m2.get("total")).compareTo(new BigDecimal((String) m1.get("total"))));
        categoryChange.sort((m1, m2) -> new BigDecimal((String) m2.get("change")).compareTo(new BigDecimal((String) m1.get("change"))));

        List<Object[]> rawTrend = txRepo.monthlyTrend(userId, range[0], range[1]);
        List<Map<String, Object>> monthly = new ArrayList<>();
        for (Object[] r : rawTrend) {
            BigDecimal mInc = new BigDecimal(r[1].toString());
            BigDecimal mExp = new BigDecimal(r[2].toString());
            BigDecimal mNet = mInc.subtract(mExp);
            Double mRate = null;
            if (mInc.compareTo(BigDecimal.ZERO) > 0) {
                mRate = mNet.divide(mInc, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
            }
            monthly.add(Map.of(
                "month", r[0],
                "income", mInc.toPlainString(),
                "expenses", mExp.toPlainString(),
                "net", mNet.toPlainString(),
                "savingsRate", mRate
            ));
        }

        List<Object[]> rawMerchants = txRepo.topMerchants(userId, range[0], range[1], PageRequest.of(0, 10));
        List<Map<String, Object>> topMerchants = new ArrayList<>();
        for (Object[] r : rawMerchants) {
            topMerchants.add(Map.of(
                "merchant", r[0],
                "total", new BigDecimal(r[1].toString()).toPlainString(),
                "count", ((Number) r[2]).intValue()
            ));
        }

        List<Object[]> rawAccounts = txRepo.sumByAccount(userId, range[0], range[1]);
        List<FinancialAccount> userAccounts = accountRepo.findActiveByUserId(userId);
        Map<String, FinancialAccount> accMap = new HashMap<>();
        for (FinancialAccount a : userAccounts) accMap.put(a.getId(), a);

        List<Map<String, Object>> byAccount = new ArrayList<>();
        for (Object[] r : rawAccounts) {
            String accId = (String) r[0];
            FinancialAccount a = accId != null ? accMap.get(accId) : null;
            if (a == null) continue;
            byAccount.add(Map.of(
                "accountId", a.getId(),
                "name", a.getName(),
                "currency", a.getCurrency(),
                "income", new BigDecimal(r[1].toString()).toPlainString(),
                "expenses", new BigDecimal(r[2].toString()).toPlainString(),
                "transactionCount", ((Number) r[3]).intValue()
            ));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseCurrency", settings.getBaseCurrency());
        data.put("period", Map.of("preset", period, "from", range[0].toString(), "to", range[1].toString()));
        data.put("previousPeriod", Map.of("from", prevFrom.toString(), "to", prevTo.toString()));
        
        Map<String, Object> totals = new HashMap<>();
        totals.put("income", totalIncome.toPlainString());
        totals.put("expenses", totalExpense.toPlainString());
        totals.put("net", net.toPlainString());
        totals.put("savingsRate", savingsRate);
        data.put("totals", totals);
        
        data.put("monthly", monthly);
        data.put("byCategory", byCategory);
        data.put("categoryChange", categoryChange);
        data.put("topMerchants", topMerchants);
        data.put("byAccount", byAccount);

        return ResponseEntity.ok(new ApiEnvelope.Success<>(data));
    }
}
