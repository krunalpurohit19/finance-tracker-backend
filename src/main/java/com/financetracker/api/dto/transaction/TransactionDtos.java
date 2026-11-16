package com.financetracker.api.dto.transaction;

import com.financetracker.api.dto.DecimalString;
import com.financetracker.api.dto.Inputs;
import com.financetracker.api.entity.Transaction;
import com.financetracker.api.entity.enums.TransactionSource;
import com.financetracker.api.entity.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Request/response shapes for /api/v1/transactions. Rules mirror mobile src/validation/transactions.ts. */
public final class TransactionDtos {

    private TransactionDtos() {}

    /**
     * The amount is always positive: direction comes from the type. The currency is the account's.
     * {@code fxRate} (account currency → base currency) overrides the saved exchange rate.
     */
    public record CreateTransactionRequest(
            @NotNull(message = "Required") TransactionType type,
            @NotNull(message = "Enter an amount")
            @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
            @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
            BigDecimal amount,
            @NotBlank(message = "Choose an account") @Size(max = 64) String accountId,
            @Size(min = 1, max = 64, message = "Choose an account") String transferAccountId,
            @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
            @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
            BigDecimal transferAmount,
            @Size(min = 1, max = 64, message = "Choose a category") String categoryId,
            @NotNull(message = "Enter a valid date as YYYY-MM-DD") LocalDate occurredOn,
            @Size(max = 120, message = "Keep this under 120 characters") String merchant,
            @Size(max = 500, message = "Keep this under 500 characters") String notes,
            @DecimalMin(value = "0", inclusive = false, message = "Exchange rate must be greater than zero")
            @Digits(integer = 10, fraction = 8, message = "Enter a valid exchange rate") BigDecimal fxRate) {
        public CreateTransactionRequest {
            accountId = Inputs.strip(accountId);
            transferAccountId = Inputs.strip(transferAccountId);
            categoryId = Inputs.strip(categoryId);
            merchant = Inputs.strip(merchant);
            notes = Inputs.strip(notes);
        }
    }

    /**
     * PATCH: a field left out is left unchanged; {@code categoryId: null} removes the category. A class,
     * not a record, so an absent Optional stays null (see SavingsDtos.UpdateGoalRequest).
     */
    @Getter @Setter
    public static class UpdateTransactionRequest {
        @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
        @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
        private BigDecimal amount;
        private LocalDate occurredOn;
        private Optional<@Size(min = 1, max = 64, message = "Choose a category") String> categoryId;
        @Size(max = 120, message = "Keep this under 120 characters")
        private String merchant;
        @Size(max = 500, message = "Keep this under 500 characters")
        private String notes;
        @DecimalMin(value = "0", inclusive = false, message = "Exchange rate must be greater than zero")
        @Digits(integer = 10, fraction = 8, message = "Enter a valid exchange rate")
        private BigDecimal fxRate;

        public void setMerchant(String merchant) { this.merchant = Inputs.strip(merchant); }
        public void setNotes(String notes) { this.notes = Inputs.strip(notes); }
    }

    public record DuplicateRequest(LocalDate occurredOn) {}

    public record TransactionResponse(String id, TransactionType type, @DecimalString BigDecimal amount, String currency,
                                      @DecimalString BigDecimal baseAmount, String baseCurrency,
                                      @DecimalString BigDecimal fxRate, String accountId, String accountName,
                                      String transferAccountId, String transferAccountName,
                                      @DecimalString BigDecimal transferAmount, String transferCurrency,
                                      String categoryId, String categoryName, String color, LocalDate occurredOn,
                                      String merchant, String notes, TransactionSource source, String recurringId,
                                      Instant createdAt) {
        public static TransactionResponse of(Transaction t) {
            return new TransactionResponse(t.getId(), t.getType(), t.getAmount(), t.getCurrency(), t.getBaseAmount(),
                    t.getBaseCurrency(), t.getFxRate(), t.getAccount().getId(), t.getAccount().getName(),
                    t.getTransferAccount() != null ? t.getTransferAccount().getId() : null,
                    t.getTransferAccount() != null ? t.getTransferAccount().getName() : null,
                    t.getTransferAmount(), t.getTransferCurrency(),
                    t.getCategory() != null ? t.getCategory().getId() : null,
                    t.getCategory() != null ? t.getCategory().getName() : null,
                    t.getCategory() != null ? t.getCategory().getColor() : null,
                    t.getOccurredOn(), t.getMerchant(), t.getNotes(), t.getSource(),
                    t.getRecurring() != null ? t.getRecurring().getId() : null, t.getCreatedAt());
        }
    }

    /** {@code nextCursor}: pass it back as {@code ?cursor=} for the next page; absent on the last page. */
    public record TransactionPage(List<TransactionResponse> items, String nextCursor) {}
}
