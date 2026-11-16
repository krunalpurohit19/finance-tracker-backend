package com.financetracker.api.dto.recurring;

import com.financetracker.api.dto.DecimalString;
import com.financetracker.api.dto.Inputs;
import com.financetracker.api.entity.RecurringTransaction;
import com.financetracker.api.entity.enums.Frequency;
import com.financetracker.api.entity.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Request/response shapes for /api/v1/recurring. Rules mirror mobile src/validation/recurring.ts. */
public final class RecurringDtos {

    private RecurringDtos() {}

    // ── Requests ─────────────────────────────────────────────────────────

    public record CreateRecurringRequest(
            @NotBlank(message = "Required") @Size(max = 60, message = "Keep this under 60 characters") String name,
            @NotNull(message = "Required") TransactionType type,
            @NotNull(message = "Enter an amount")
            @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
            @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
            BigDecimal amount,
            @Pattern(regexp = "[A-Z]{3}", message = "Enter a 3-letter currency code") String currency,
            @NotBlank(message = "Choose an account") @Size(max = 64) String accountId,
            @Size(min = 1, max = 64, message = "Choose an account") String transferAccountId,
            @Size(min = 1, max = 64, message = "Choose a category") String categoryId,
            @Size(max = 120, message = "Keep this under 120 characters") String merchant,
            @Size(max = 500, message = "Keep this under 500 characters") String notes,
            @NotNull(message = "Required") Frequency frequency,
            @Min(value = 1, message = "Between 1 and 365") @Max(value = 365, message = "Between 1 and 365") Integer interval,
            @NotNull(message = "Required") LocalDate startOn,
            LocalDate endOn,
            @Min(value = 1, message = "Between 1 and 31") @Max(value = 31, message = "Between 1 and 31") Integer dayOfMonth) {
        public CreateRecurringRequest {
            name = Inputs.strip(name);
            currency = Inputs.currency(currency);
            accountId = Inputs.strip(accountId);
            transferAccountId = Inputs.strip(transferAccountId);
            categoryId = Inputs.strip(categoryId);
            merchant = Inputs.strip(merchant);
            notes = Inputs.strip(notes);
        }
    }

    /**
     * PATCH: a field left out is left unchanged; {@code endOn: null} removes the end date and
     * {@code categoryId: null} the category. A class, not a record, so an absent Optional stays null
     * (see SavingsDtos.UpdateGoalRequest). Resuming a paused rule skips the dates it was paused for.
     */
    @Getter @Setter
    public static class UpdateRecurringRequest {
        @Size(min = 1, message = "Required") @Size(max = 60, message = "Keep this under 60 characters")
        private String name;
        @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
        @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
        private BigDecimal amount;
        private Boolean isActive;
        private Optional<LocalDate> endOn;
        private Optional<@Size(min = 1, max = 64, message = "Choose a category") String> categoryId;
        @Size(max = 120, message = "Keep this under 120 characters")
        private String merchant;
        @Size(max = 500, message = "Keep this under 500 characters")
        private String notes;

        public void setName(String name) { this.name = Inputs.strip(name); }
        public void setMerchant(String merchant) { this.merchant = Inputs.strip(merchant); }
        public void setNotes(String notes) { this.notes = Inputs.strip(notes); }
    }

    // ── Responses ────────────────────────────────────────────────────────

    public record RecurringResponse(String id, String name, TransactionType type, @DecimalString BigDecimal amount,
                                    String currency, String accountId, String accountName, String transferAccountId,
                                    String categoryId, String categoryName, String merchant, Frequency frequency,
                                    int interval, LocalDate startOn, LocalDate endOn, Integer dayOfMonth,
                                    LocalDate nextOccurrence, LocalDate lastGenerated, boolean isActive,
                                    boolean autoPost) {
        public static RecurringResponse of(RecurringTransaction r) {
            return new RecurringResponse(r.getId(), r.getName(), r.getType(), r.getAmount(), r.getCurrency(),
                    r.getAccount().getId(), r.getAccount().getName(),
                    r.getTransferAccount() != null ? r.getTransferAccount().getId() : null,
                    r.getCategory() != null ? r.getCategory().getId() : null,
                    r.getCategory() != null ? r.getCategory().getName() : null,
                    r.getMerchant(), r.getFrequency(), r.getInterval(), r.getStartOn(), r.getEndOn(),
                    r.getDayOfMonth(), r.getNextOccurrence(), r.getLastGenerated(), r.isActive(), r.isAutoPost());
        }
    }

    /**
     * What one generate call did. {@code created}: transactions posted ({@code generated} is the legacy
     * name for the same number). {@code skipped}: due rules held back this run — no exchange rate for the
     * date, an archived or deleted account, or a currency that doesn't match the account; they catch up
     * once fixed. {@code rulesProcessed}: due rules examined.
     */
    public record GenerationResult(int generated, int created, int skipped, int rulesProcessed) {}
}
