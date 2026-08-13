package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

    public DashboardController(TransactionRepository txRepo, UserSettingsRepository settingsRepo) {
        this.txRepo = txRepo;
        this.settingsRepo = settingsRepo;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> dashboard(
            @RequestParam(defaultValue = "this-month") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String userId = SecurityUtils.currentUserId();
        var settings = settingsRepo.findById(userId).orElseThrow();
        LocalDate[] range = resolvePeriod(period, from, to, settings.getTimezone());

        Object[] sums = txRepo.sumIncomeAndExpense(userId, range[0], range[1]);
        BigDecimal income = (BigDecimal) sums[0];
        BigDecimal expense = (BigDecimal) sums[1];

        var recent = txRepo.findFiltered(userId, null, null, null, range[0], range[1], PageRequest.of(0, 5));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("income", income.toPlainString());
        data.put("expense", expense.toPlainString());
        data.put("net", income.subtract(expense).toPlainString());
        data.put("baseCurrency", settings.getBaseCurrency());
        data.put("recentTransactions", recent.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("type", t.getType().name());
            m.put("amount", t.getAmount().toPlainString());
            m.put("currency", t.getCurrency());
            m.put("baseAmount", t.getBaseAmount().toPlainString());
            m.put("categoryId", t.getCategory() != null ? t.getCategory().getId() : null);
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
            case "custom" -> new LocalDate[]{LocalDate.parse(from), LocalDate.parse(to)};
            default -> new LocalDate[]{today.withDayOfMonth(1), today};
        };
    }
}
