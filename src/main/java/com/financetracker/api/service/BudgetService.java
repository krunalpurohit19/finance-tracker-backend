package com.financetracker.api.service;

import com.financetracker.api.dto.budget.BudgetDtos.*;
import com.financetracker.api.entity.Budget;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.enums.BudgetPeriod;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.BudgetRepository;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BudgetService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** DECIMAL(18,4) zero, as MySQL returns an empty SUM: "0.0000", not "0". */
    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    /** Percent of the budget at which it counts as near its limit. */
    static final BigDecimal NEAR_LIMIT_PERCENT = new BigDecimal("85");
    private static final LocalDate OPEN_ENDED = LocalDate.of(9999, 12, 31);

    private final BudgetRepository budgetRepo;
    private final TransactionRepository txRepo;
    private final CategoryRepository categoryRepo;
    private final UserRepository userRepo;

    public BudgetService(BudgetRepository budgetRepo, TransactionRepository txRepo, CategoryRepository categoryRepo,
                         UserRepository userRepo) {
        this.budgetRepo = budgetRepo;
        this.txRepo = txRepo;
        this.categoryRepo = categoryRepo;
        this.userRepo = userRepo;
    }

    /**
     * Budgets in force during {@code month} (default: this month) with what was spent against each:
     * a category budget counts that category's expenses, the overall budget counts all of them
     * (uncategorised included). Transfers never count. One grouped query, not one per budget.
     */
    @Transactional(readOnly = true)
    public List<BudgetProgress> list(String userId, YearMonth month) {
        YearMonth ym = month != null ? month : YearMonth.now();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<Budget> budgets = budgetRepo.findEffectiveForMonth(userId, start, end);
        if (budgets.isEmpty()) return List.of();

        Map<String, BigDecimal> byCategory = new HashMap<>();
        BigDecimal total = ZERO;
        for (Object[] row : txRepo.sumByCategory(userId, TransactionType.EXPENSE, start, end)) {
            BigDecimal sum = new BigDecimal(row[1].toString());
            byCategory.put((String) row[0], sum);
            total = total.add(sum);
        }
        BigDecimal overall = total;
        return budgets.stream()
                .map(b -> progress(b, b.getCategory() == null ? overall
                        : byCategory.getOrDefault(b.getCategory().getId(), ZERO), ym))
                .toList();
    }

    /** Spend per month for the last {@code months} months, newest first; {@code categoryId} null = all spending. */
    @Transactional(readOnly = true)
    public List<MonthSpend> history(String userId, int months, String categoryId) {
        List<MonthSpend> result = new ArrayList<>();
        YearMonth current = YearMonth.now();
        // ponytail: one SUM per month (≤ 24, bounded by the controller); a GROUP BY month query if it ever matters.
        for (int i = 0; i < months; i++) {
            YearMonth ym = current.minusMonths(i);
            result.add(new MonthSpend(ym, txRepo.sumExpenseForBudget(userId, categoryId, ym.atDay(1), ym.atEndOfMonth())));
        }
        return result;
    }

    @Transactional
    public BudgetResponse create(String userId, CreateBudgetRequest req) {
        Category category = req.categoryId() != null ? expenseCategory(userId, req.categoryId()) : null;
        LocalDate from = req.effectiveFrom() != null ? req.effectiveFrom() : YearMonth.now().atDay(1);
        checkRange(from, req.effectiveTo());
        checkNoOverlap(userId, req.categoryId(), "", from, req.effectiveTo());
        Budget budget = Budget.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .category(category)
                .amount(req.amount().setScale(4)) // DECIMAL(18,4); @Digits guarantees this is exact
                .period(BudgetPeriod.MONTHLY)
                .effectiveFrom(from)
                .effectiveTo(req.effectiveTo())
                .build();
        budgetRepo.save(budget);
        return BudgetResponse.of(budget);
    }

    @Transactional
    public BudgetResponse update(String userId, String id, UpdateBudgetRequest req) {
        Budget budget = budget(userId, id);
        if (req.effectiveTo() != null) {
            checkRange(budget.getEffectiveFrom(), req.effectiveTo());
            checkNoOverlap(userId, categoryId(budget), budget.getId(), budget.getEffectiveFrom(), req.effectiveTo());
            budget.setEffectiveTo(req.effectiveTo());
        }
        if (req.amount() != null) budget.setAmount(req.amount().setScale(4));
        return BudgetResponse.of(budget);
    }

    @Transactional
    public void remove(String userId, String id) {
        budget(userId, id).setDeletedAt(Instant.now());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    static BudgetProgress progress(Budget b, BigDecimal spent, YearMonth month) {
        BigDecimal percentUsed = null;
        BudgetStatus status = BudgetStatus.OK;
        if (b.getAmount().signum() > 0) {
            percentUsed = spent.multiply(HUNDRED).divide(b.getAmount(), 2, RoundingMode.HALF_UP);
            if (percentUsed.compareTo(HUNDRED) >= 0) status = BudgetStatus.EXCEEDED;
            else if (percentUsed.compareTo(NEAR_LIMIT_PERCENT) >= 0) status = BudgetStatus.NEAR_LIMIT;
        }
        Category c = b.getCategory();
        return new BudgetProgress(b.getId(), categoryId(b), b.getAmount(), b.getPeriod(), b.getEffectiveFrom(),
                b.getEffectiveTo(), spent, b.getAmount().subtract(spent),
                c != null ? c.getName() : "Overall Budget", c != null ? c.getColor() : null, month,
                percentUsed, status);
    }

    private static String categoryId(Budget b) {
        return b.getCategory() != null ? b.getCategory().getId() : null;
    }

    private Budget budget(String userId, String id) {
        return budgetRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Budget not found"));
    }

    private Category expenseCategory(String userId, String categoryId) {
        Category c = categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(categoryId, userId)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        if (c.getKind() != CategoryKind.EXPENSE) {
            throw invalid("categoryId", "Budgets can only track expense categories");
        }
        return c;
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (to != null && to.isBefore(from)) throw invalid("effectiveTo", "The end can't be before the start");
    }

    // ponytail: check-then-insert, not locked; two simultaneous creates for the same category can both pass.
    // A range overlap can't be a unique key; SELECT ... FOR UPDATE on the category's budgets if it ever happens.
    private void checkNoOverlap(String userId, String categoryId, String excludeId, LocalDate from, LocalDate to) {
        if (budgetRepo.countOverlapping(userId, categoryId, excludeId, from, to != null ? to : OPEN_ENDED) > 0) {
            throw ApiException.budgetOverlap("A budget already covers that period");
        }
    }

    private static ApiException invalid(String field, String message) {
        return ApiException.validationFailed(message, Map.of(field, List.of(message)));
    }
}
