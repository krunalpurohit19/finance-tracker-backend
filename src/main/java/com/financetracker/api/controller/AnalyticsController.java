package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final TransactionRepository txRepo;
    private final UserSettingsRepository settingsRepo;

    public AnalyticsController(TransactionRepository txRepo, UserSettingsRepository settingsRepo) {
        this.txRepo = txRepo;
        this.settingsRepo = settingsRepo;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> analytics(
            @RequestParam(defaultValue = "this-month") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String userId = SecurityUtils.currentUserId();
        var settings = settingsRepo.findById(userId).orElseThrow();
        LocalDate[] range = DashboardController.resolvePeriod(period, from, to, settings.getTimezone());

        Object[] sums = txRepo.sumIncomeAndExpense(userId, range[0], range[1]);
        BigDecimal totalIncome = (BigDecimal) sums[0];
        BigDecimal totalExpense = (BigDecimal) sums[1];

        List<Object[]> expenseByCategory = txRepo.sumByCategory(userId, TransactionType.EXPENSE, range[0], range[1]);
        List<Object[]> incomeByCategory = txRepo.sumByCategory(userId, TransactionType.INCOME, range[0], range[1]);
        List<Object[]> trend = txRepo.monthlyTrend(userId, range[0], range[1]);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalIncome", totalIncome.toPlainString());
        data.put("totalExpense", totalExpense.toPlainString());
        data.put("net", totalIncome.subtract(totalExpense).toPlainString());
        data.put("baseCurrency", settings.getBaseCurrency());
        data.put("expenseByCategory", expenseByCategory.stream().map(r -> Map.of(
                "categoryId", r[0] != null ? r[0] : "uncategorized",
                "total", ((BigDecimal) r[1]).toPlainString()
        )).toList());
        data.put("incomeByCategory", incomeByCategory.stream().map(r -> Map.of(
                "categoryId", r[0] != null ? r[0] : "uncategorized",
                "total", ((BigDecimal) r[1]).toPlainString()
        )).toList());
        data.put("trend", trend.stream().map(r -> Map.of(
                "month", r[0],
                "income", ((BigDecimal) r[1]).toPlainString(),
                "expense", ((BigDecimal) r[2]).toPlainString()
        )).toList());
        return ResponseEntity.ok(new ApiEnvelope.Success<>(data));
    }
}
