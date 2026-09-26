package com.financetracker.api.dto.budget;

import com.financetracker.api.dto.DecimalString;
import com.financetracker.api.dto.Inputs;
import com.financetracker.api.entity.Budget;
import com.financetracker.api.entity.enums.BudgetPeriod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** Request/response shapes for /api/v1/budgets. Rules mirror mobile src/validation/budgets.ts. */
public final class BudgetDtos {

    private BudgetDtos() {}

    public enum BudgetStatus { OK, NEAR_LIMIT, EXCEEDED }

    // ── Requests ─────────────────────────────────────────────────────────

    /** {@code categoryId} omitted = one overall budget across every category. Amounts are in the base currency. */
    public record CreateBudgetRequest(
            @Size(min = 1, max = 64, message = "Choose a category") String categoryId,
            @NotNull(message = "Enter an amount")
            @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
            @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
            BigDecimal amount,
            @NotNull(message = "Required") LocalDate effectiveFrom,
            LocalDate effectiveTo) {
        public CreateBudgetRequest { categoryId = Inputs.strip(categoryId); }
    }

    /** PATCH: a field left out (null) is left unchanged. */
    public record UpdateBudgetRequest(
            @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
            @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
            BigDecimal amount,
            LocalDate effectiveTo) {}

    // ── Responses ────────────────────────────────────────────────────────

    public record BudgetResponse(String id, String categoryId, @DecimalString BigDecimal amount, BudgetPeriod period,
                                 LocalDate effectiveFrom, LocalDate effectiveTo) {
        public static BudgetResponse of(Budget b) {
            return new BudgetResponse(b.getId(), b.getCategory() != null ? b.getCategory().getId() : null,
                    b.getAmount(), b.getPeriod(), b.getEffectiveFrom(), b.getEffectiveTo());
        }
    }

    /** A budget with this month's spend. {@code percentUsed} is a JSON number (mobile calls toFixed on it). */
    public record BudgetProgress(String id, String categoryId, @DecimalString BigDecimal amount, BudgetPeriod period,
                                 LocalDate effectiveFrom, LocalDate effectiveTo,
                                 @DecimalString BigDecimal spent, @DecimalString BigDecimal remaining,
                                 String categoryName, String color, YearMonth month,
                                 BigDecimal percentUsed, BudgetStatus status) {}

    public record MonthSpend(YearMonth month, @DecimalString BigDecimal spent) {}
}
