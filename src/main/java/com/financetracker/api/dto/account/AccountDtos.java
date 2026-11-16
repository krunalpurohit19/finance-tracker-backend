package com.financetracker.api.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.financetracker.api.dto.DecimalString;
import com.financetracker.api.dto.Inputs;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.enums.AccountClass;
import com.financetracker.api.entity.enums.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request/response shapes for /api/v1/accounts. Rules mirror mobile src/validation/accounts.ts. */
public final class AccountDtos {

    private AccountDtos() {}

    /** {@code class} defaults from the type (a credit card is a liability). The opening balance may be negative. */
    public record CreateAccountRequest(
            @NotBlank(message = "Required") @Size(max = 60, message = "Keep this under 60 characters") String name,
            @NotNull(message = "Required") AccountType type,
            @JsonProperty("class") AccountClass accountClass,
            @NotNull(message = "Enter a 3-letter currency code")
            @Pattern(regexp = "[A-Z]{3}", message = "Enter a 3-letter currency code") String currency,
            @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
            BigDecimal openingBalance,
            @Size(max = 80, message = "Keep this under 80 characters") String institution,
            @Pattern(regexp = "\\d{4}", message = "Enter the last 4 digits") String last4,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Enter a colour as #RRGGBB") String color,
            @Size(max = 40, message = "Keep this under 40 characters") String icon,
            Boolean isDefault) {
        public CreateAccountRequest {
            name = Inputs.strip(name);
            currency = Inputs.currency(currency);
            institution = Inputs.strip(institution);
            last4 = Inputs.strip(last4);
            color = Inputs.strip(color);
            icon = Inputs.strip(icon);
        }
    }

    /** PATCH: a field left out (or null) is left unchanged. The currency can't change: balances are in it. */
    public record UpdateAccountRequest(
            @Size(min = 1, message = "Required") @Size(max = 60, message = "Keep this under 60 characters") String name,
            AccountType type,
            @JsonProperty("class") AccountClass accountClass,
            @Digits(integer = 14, fraction = 4, message = "Enter a valid amount with up to 4 decimal places")
            BigDecimal openingBalance,
            @Size(max = 80, message = "Keep this under 80 characters") String institution,
            @Pattern(regexp = "\\d{4}", message = "Enter the last 4 digits") String last4,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Enter a colour as #RRGGBB") String color,
            @Size(max = 40, message = "Keep this under 40 characters") String icon,
            Boolean isDefault) {
        public UpdateAccountRequest {
            name = Inputs.strip(name);
            institution = Inputs.strip(institution);
            last4 = Inputs.strip(last4);
            color = Inputs.strip(color);
            icon = Inputs.strip(icon);
        }
    }

    public record ReorderRequest(
            @NotEmpty(message = "Provide at least one account") @Size(max = 500)
            List<@NotBlank(message = "Required") String> orderedIds) {}

    public record Reordered(int reordered) {}

    /** {@code archived} is what the app reads; {@code archivedAt} is kept for older clients. */
    public record AccountResponse(String id, String name, AccountType type, @JsonProperty("class") AccountClass accountClass,
                                  String currency, @DecimalString BigDecimal openingBalance, String institution,
                                  String last4, String color, String icon, boolean isDefault, int sortOrder,
                                  boolean archived, Instant archivedAt, Instant createdAt,
                                  @DecimalString BigDecimal balance) {
        public static AccountResponse of(FinancialAccount a, BigDecimal balance) {
            return new AccountResponse(a.getId(), a.getName(), a.getType(), a.getAccountClass(), a.getCurrency(),
                    a.getOpeningBalance(), a.getInstitution(), a.getLast4(), a.getColor(), a.getIcon(), a.isDefault(),
                    a.getSortOrder(), a.getArchivedAt() != null, a.getArchivedAt(), a.getCreatedAt(), balance);
        }
    }
}
