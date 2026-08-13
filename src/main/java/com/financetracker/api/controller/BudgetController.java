package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.*;
import com.financetracker.api.entity.enums.BudgetPeriod;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.*;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetRepository budgetRepo;
    private final TransactionRepository txRepo;
    private final CategoryRepository categoryRepo;

    public BudgetController(BudgetRepository budgetRepo, TransactionRepository txRepo, CategoryRepository categoryRepo) {
        this.budgetRepo = budgetRepo;
        this.txRepo = txRepo;
        this.categoryRepo = categoryRepo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> list(@RequestParam(required = false) String month) {
        String userId = SecurityUtils.currentUserId();
        YearMonth ym = month != null ? YearMonth.parse(month) : YearMonth.now();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Budget> budgets = budgetRepo.findEffectiveForMonth(userId, start, end);
        List<Map<String, Object>> result = budgets.stream().map(b -> {
            BigDecimal spent = txRepo.sumExpenseForBudget(userId, b.getCategory() != null ? b.getCategory().getId() : null, start, end);
            Map<String, Object> m = toMap(b);
            
            BigDecimal remaining = b.getAmount().subtract(spent);
            m.put("spent", spent.toPlainString());
            m.put("remaining", remaining.toPlainString());
            
            m.put("categoryName", b.getCategory() != null ? b.getCategory().getName() : "Overall Budget");
            m.put("color", b.getCategory() != null ? b.getCategory().getColor() : null);
            m.put("month", ym.toString());
            
            Double percentUsed = null;
            String status = "OK";
            if (b.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                percentUsed = spent.divide(b.getAmount(), 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
                if (percentUsed >= 100.0) {
                    status = "EXCEEDED";
                } else if (percentUsed >= 85.0) {
                    status = "NEAR_LIMIT";
                }
            }
            m.put("percentUsed", percentUsed);
            m.put("status", status);
            
            return m;
        }).toList();
        return ResponseEntity.ok(new ApiEnvelope.Success<>(result));
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> history(
            @RequestParam(defaultValue = "6") int months, @RequestParam(required = false) String categoryId) {
        String userId = SecurityUtils.currentUserId();
        List<Map<String, Object>> result = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 0; i < months; i++) {
            YearMonth ym = current.minusMonths(i);
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            BigDecimal spent = txRepo.sumExpenseForBudget(userId, categoryId, start, end);
            result.add(Map.of("month", ym.toString(), "spent", spent.toPlainString()));
        }
        return ResponseEntity.ok(new ApiEnvelope.Success<>(result));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.currentUserId();
        String categoryId = (String) body.get("categoryId");
        LocalDate effectiveFrom = LocalDate.parse((String) body.get("effectiveFrom"));
        LocalDate effectiveTo = body.containsKey("effectiveTo") ? LocalDate.parse((String) body.get("effectiveTo")) : null;

        long overlaps = budgetRepo.countOverlapping(userId, categoryId, "", effectiveFrom,
                effectiveTo != null ? effectiveTo : LocalDate.of(9999, 12, 31));
        if (overlaps > 0) throw ApiException.budgetOverlap("A budget already covers that period");

        Budget budget = Budget.builder()
                .id(UUID.randomUUID().toString())
                .user(User.builder().id(userId).build())
                .amount(new BigDecimal((String) body.get("amount")))
                .period(BudgetPeriod.MONTHLY)
                .effectiveFrom(effectiveFrom)
                .effectiveTo(effectiveTo)
                .build();
        if (categoryId != null) {
            budget.setCategory(categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(categoryId, userId).orElseThrow());
        }
        budgetRepo.save(budget);
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(toMap(budget)));
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> update(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.currentUserId();
        Budget budget = budgetRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Budget not found"));
        if (body.containsKey("amount")) budget.setAmount(new BigDecimal((String) body.get("amount")));
        if (body.containsKey("effectiveTo")) budget.setEffectiveTo(LocalDate.parse((String) body.get("effectiveTo")));
        budgetRepo.save(budget);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(toMap(budget)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> remove(@PathVariable String id) {
        String userId = SecurityUtils.currentUserId();
        Budget budget = budgetRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Budget not found"));
        budget.setDeletedAt(java.time.Instant.now());
        budgetRepo.save(budget);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("id", id)));
    }

    private Map<String, Object> toMap(Budget b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("categoryId", b.getCategory() != null ? b.getCategory().getId() : null);
        m.put("amount", b.getAmount().toPlainString());
        m.put("period", b.getPeriod().name());
        m.put("effectiveFrom", b.getEffectiveFrom().toString());
        m.put("effectiveTo", b.getEffectiveTo() != null ? b.getEffectiveTo().toString() : null);
        return m;
    }
}
