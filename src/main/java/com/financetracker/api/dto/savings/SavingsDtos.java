package com.financetracker.api.dto.savings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.financetracker.api.dto.DecimalString;
import com.financetracker.api.dto.Inputs;
import com.financetracker.api.entity.GoalContribution;
import com.financetracker.api.entity.enums.GoalStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Request/response shapes for /api/v1/savings. Rules mirror mobile src/validation/goals.ts. Amounts are base currency. */
public final class SavingsDtos {

    private SavingsDtos() {}

    static final String HEX_COLOR = "#[0-9a-fA-F]{6}";

    // ── Requests ─────────────────────────────────────────────────────────

    public record CreateGoalRequest(
            @NotBlank(message = "Required") @Size(max = 60, message = "Keep this under 60 characters") String name,
            @NotNull(message = "Enter an amount")
            @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
            @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
            BigDecimal targetAmount,
            LocalDate targetDate,
            @Pattern(regexp = HEX_COLOR, message = "Enter a colour as #RRGGBB") String color,
            @Size(max = 40, message = "Keep this under 40 characters") String icon) {
        public CreateGoalRequest {
            name = Inputs.strip(name);
            icon = Inputs.strip(icon);
        }
    }

    /**
     * PATCH: a field left out is left unchanged. {@code targetDate} may also be sent as null to remove the
     * deadline. A class, not a record: Jackson only calls a setter for keys that are present, so absent
     * stays null (unchanged) while JSON null becomes Optional.empty() (clear). A record would turn an
     * absent Optional into Optional.empty() and wipe the deadline on every PATCH.
     */
    @Getter @Setter
    public static class UpdateGoalRequest {
        @Size(min = 1, message = "Required") @Size(max = 60, message = "Keep this under 60 characters")
        private String name;
        @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
        @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
        private BigDecimal targetAmount;
        private Optional<LocalDate> targetDate;
        @Pattern(regexp = HEX_COLOR, message = "Enter a colour as #RRGGBB")
        private String color;
        @Size(max = 40, message = "Keep this under 40 characters")
        private String icon;
        private GoalStatus status;

        public void setName(String name) { this.name = Inputs.strip(name); }
        public void setIcon(String icon) { this.icon = Inputs.strip(icon); }
    }

    /** A contribution designates money toward a goal; negative is a withdrawal, zero means nothing. */
    public record ContributeRequest(
            @NotNull(message = "Enter an amount")
            @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
            BigDecimal amount,
            LocalDate occurredOn, // defaults to today: the mobile client sends only {amount}
            @Size(max = 200, message = "Keep this under 200 characters") String notes) {
        public ContributeRequest { notes = Inputs.strip(notes); }
    }

    // ── Responses ────────────────────────────────────────────────────────

    /** Rates are percentages (JSON numbers); money is JSON strings. Months are "YYYY-MM". */
    public record SavingsSummary(String baseCurrency, @DecimalString BigDecimal lifetime,
                                 @DecimalString BigDecimal thisMonth, BigDecimal thisMonthRate,
                                 @DecimalString BigDecimal averageMonthly, MonthSaved best, MonthSaved worst,
                                 List<TrendPoint> trend) {}

    public record MonthSaved(String month, @DecimalString BigDecimal saved) {}

    /** {@code rate} is null (not omitted) for a month without income, matching the mobile type {@code number | null}. */
    public record TrendPoint(String month, @DecimalString BigDecimal income, @DecimalString BigDecimal expenses,
                             @DecimalString BigDecimal saved, @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal rate) {}

    public record GoalResponse(String id, String name, @DecimalString BigDecimal targetAmount,
                               @DecimalString BigDecimal saved, @DecimalString BigDecimal remaining,
                               BigDecimal progress, LocalDate targetDate, Long monthsRemaining,
                               @DecimalString BigDecimal requiredMonthly, boolean overdue, boolean achieved,
                               String color, String icon, GoalStatus status, boolean archived) {}

    public record ContributionResponse(String id, @DecimalString BigDecimal amount, LocalDate occurredOn,
                                       String transactionId, String notes, Instant createdAt) {
        public static ContributionResponse of(GoalContribution c) {
            return new ContributionResponse(c.getId(), c.getAmount(), c.getOccurredOn(),
                    c.getTransaction() != null ? c.getTransaction().getId() : null, c.getNotes(), c.getCreatedAt());
        }
    }
}
