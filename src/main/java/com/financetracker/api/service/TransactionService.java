package com.financetracker.api.service;

import com.financetracker.api.dto.transaction.TransactionDtos.*;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.Transaction;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.entity.enums.TransactionSource;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ledger. One row per transaction; a transfer is ONE row with two accounts (never an expense).
 * Amounts are positive, in the account's currency; baseAmount is the same money in the base currency.
 */
@Service
public class TransactionService {

    private final TransactionRepository txRepo;
    private final FinancialAccountRepository accountRepo;
    private final CategoryRepository categoryRepo;
    private final ExchangeRateRepository rateRepo;
    private final UserSettingsRepository settingsRepo;
    private final UserRepository userRepo;

    public TransactionService(TransactionRepository txRepo, FinancialAccountRepository accountRepo,
                              CategoryRepository categoryRepo, ExchangeRateRepository rateRepo,
                              UserSettingsRepository settingsRepo, UserRepository userRepo) {
        this.txRepo = txRepo;
        this.accountRepo = accountRepo;
        this.categoryRepo = categoryRepo;
        this.rateRepo = rateRepo;
        this.settingsRepo = settingsRepo;
        this.userRepo = userRepo;
    }

    /**
     * Newest first, {@code limit} per page. {@code cursor} is the previous page's nextCursor (the id of
     * its last row); the page continues strictly after that row, so rows added meanwhile never repeat.
     */
    @Transactional(readOnly = true)
    public TransactionPage list(String userId, TransactionType type, String accountId, String categoryId,
                                LocalDate from, LocalDate to, String cursor, int limit) {
        Transaction after = null;
        if (cursor != null) {
            // Deliberately not filtered on deletedAt: deleting the last row of a page must not break paging.
            after = txRepo.findById(cursor).filter(t -> t.getUser().getId().equals(userId))
                    .orElseThrow(() -> invalid("cursor", "Invalid value"));
        }
        List<Transaction> rows = txRepo.findPage(userId, type, accountId, categoryId, from, to,
                after != null ? after.getOccurredOn() : null, after != null ? after.getCreatedAt() : null,
                after != null ? after.getId() : null, PageRequest.of(0, limit + 1));
        boolean hasMore = rows.size() > limit;
        List<Transaction> page = hasMore ? rows.subList(0, limit) : rows;
        return new TransactionPage(page.stream().map(TransactionResponse::of).toList(),
                hasMore ? page.getLast().getId() : null);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(String userId, String id) {
        return TransactionResponse.of(transaction(userId, id));
    }

    @Transactional
    public TransactionResponse create(String userId, CreateTransactionRequest req) {
        return create(userId, req, null);
    }

    /**
     * @param idempotencyKey the request's Idempotency-Key header, or null. A retry with the same key (a
     *                       timeout, a double tap) returns the transaction the first attempt created
     *                       instead of posting again. Two simultaneous attempts: the unique key makes
     *                       the loser a 409, and its retry finds the winner.
     */
    @Transactional
    public TransactionResponse create(String userId, CreateTransactionRequest req, String idempotencyKey) {
        if (idempotencyKey != null) {
            var earlier = txRepo.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (earlier.isPresent()) {
                Transaction t = earlier.get();
                // The same key with different details is a new transaction sent with a stale key, not a retry.
                if (t.getType() != req.type() || t.getAmount().compareTo(req.amount()) != 0
                        || !t.getAccount().getId().equals(req.accountId()) || !t.getOccurredOn().equals(req.occurredOn())) {
                    throw ApiException.conflict("This was already saved with different details. Check your activity before trying again.");
                }
                return TransactionResponse.of(t);
            }
        }
        // FOR SHARE, and first: a base-currency change (FOR UPDATE) waits for this insert, then sees it.
        UserSettings settings = settingsForShare(userId);
        FinancialAccount account = usableAccount(userId, req.accountId(), "Account not found");
        BigDecimal amount = req.amount().setScale(4); // DECIMAL(18,4); @Digits makes this exact
        // An override only means something for a foreign-currency account: base → base is always 1.
        boolean foreign = !account.getCurrency().equals(settings.getBaseCurrency());
        BigDecimal fxRate = foreign && req.fxRate() != null ? req.fxRate()
                : fxRate(userId, account.getCurrency(), settings.getBaseCurrency(), req.occurredOn());

        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .type(req.type())
                .amount(amount)
                .currency(account.getCurrency())
                .baseAmount(baseAmount(amount, fxRate))
                .baseCurrency(settings.getBaseCurrency())
                .fxRate(fxRate)
                .account(account)
                .occurredOn(req.occurredOn())
                .merchant(req.merchant())
                .notes(req.notes())
                .source(TransactionSource.MANUAL)
                .idempotencyKey(idempotencyKey)
                .build();

        if (req.type() == TransactionType.TRANSFER) {
            if (req.transferAccountId() == null) throw invalid("transferAccountId", "Choose a destination account");
            if (req.transferAccountId().equals(req.accountId())) throw ApiException.sameAccountTransfer();
            if (req.categoryId() != null) throw invalid("categoryId", "Transfers don't have a category");
            FinancialAccount to = usableAccount(userId, req.transferAccountId(), "Transfer account not found");
            tx.setTransferAccount(to);
            tx.setTransferCurrency(to.getCurrency());
            tx.setTransferAmount(received(userId, amount, account, to, req.transferAmount(), req.occurredOn()));
        } else {
            if (req.transferAccountId() != null) {
                throw invalid("transferAccountId", "Only transfers have a destination account");
            }
            if (req.categoryId() != null) tx.setCategory(category(userId, req.categoryId(), req.type()));
        }
        txRepo.save(tx);
        return TransactionResponse.of(tx);
    }

    @Transactional
    public TransactionResponse update(String userId, String id, UpdateTransactionRequest req) {
        UserSettings settings = settingsForShare(userId);
        Transaction tx = transaction(userId, id);
        boolean transfer = tx.getType() == TransactionType.TRANSFER;

        if (req.getMerchant() != null) tx.setMerchant(req.getMerchant());
        if (req.getNotes() != null) tx.setNotes(req.getNotes());
        if (req.getCategoryId() != null) {
            if (transfer && req.getCategoryId().isPresent()) throw invalid("categoryId", "Transfers don't have a category");
            tx.setCategory(req.getCategoryId().map(c -> category(userId, c, tx.getType())).orElse(null));
        }

        boolean dateChanged = req.getOccurredOn() != null && !req.getOccurredOn().equals(tx.getOccurredOn());
        if (req.getOccurredOn() != null) tx.setOccurredOn(req.getOccurredOn());
        boolean crossCurrency = transfer && !tx.getCurrency().equals(tx.getTransferCurrency());
        if (req.getTransferAmount() != null && !crossCurrency) {
            throw invalid("transferAmount", "Only transfers between different currencies have a separate received amount");
        }
        if (req.getAmount() != null) {
            BigDecimal amount = req.getAmount().setScale(4);
            // Keeping the old received amount would leave the two accounts out of step.
            if (crossCurrency && req.getTransferAmount() == null && amount.compareTo(tx.getAmount()) != 0) {
                throw invalid("transferAmount", "Enter the amount received in " + tx.getTransferCurrency());
            }
            if (transfer && !crossCurrency) tx.setTransferAmount(amount);
            tx.setAmount(amount);
        }
        if (req.getTransferAmount() != null) tx.setTransferAmount(req.getTransferAmount().setScale(4));
        // The rate the transaction was booked at stays, unless a new one is given or the date moved.
        if (req.getFxRate() != null && !tx.getCurrency().equals(tx.getBaseCurrency())) {
            tx.setFxRate(req.getFxRate());
        } else if (dateChanged && !tx.getCurrency().equals(tx.getBaseCurrency())) {
            tx.setFxRate(fxRate(userId, tx.getCurrency(), settings.getBaseCurrency(), tx.getOccurredOn()));
        }
        tx.setBaseAmount(baseAmount(tx.getAmount(), tx.getFxRate()));
        return TransactionResponse.of(tx);
    }

    @Transactional
    public void remove(String userId, String id) {
        transaction(userId, id).setDeletedAt(Instant.now());
    }

    /** A copy dated {@code occurredOn} (default: the original's date), always MANUAL and unlinked from any rule. */
    @Transactional
    public TransactionResponse duplicate(String userId, String id, LocalDate occurredOn) {
        settingsForShare(userId);
        Transaction src = transaction(userId, id);
        if (src.getAccount().getArchivedAt() != null
                || src.getTransferAccount() != null && src.getTransferAccount().getArchivedAt() != null) {
            throw ApiException.accountArchived("That account is archived");
        }
        Category category = src.getCategory();
        Transaction dup = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .type(src.getType())
                .amount(src.getAmount())
                .currency(src.getCurrency())
                .baseAmount(src.getBaseAmount())
                .baseCurrency(src.getBaseCurrency())
                .fxRate(src.getFxRate())
                .account(src.getAccount())
                .transferAccount(src.getTransferAccount())
                .transferAmount(src.getTransferAmount())
                .transferCurrency(src.getTransferCurrency())
                .category(category != null && category.getDeletedAt() == null ? category : null)
                .occurredOn(occurredOn != null ? occurredOn : src.getOccurredOn())
                .merchant(src.getMerchant())
                .notes(src.getNotes())
                .source(TransactionSource.MANUAL)
                .build();
        txRepo.save(dup);
        return TransactionResponse.of(dup);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Largest DECIMAL(18,4). */
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999999999.9999");

    private static BigDecimal baseAmount(BigDecimal amount, BigDecimal fxRate) {
        return fits(amount.multiply(fxRate).setScale(4, RoundingMode.HALF_UP));
    }

    /** A converted amount can outgrow the column even when the entered one fits: say so, as a 422. */
    private static BigDecimal fits(BigDecimal converted) {
        if (converted.compareTo(MAX_MONEY) > 0) throw invalid("amount", "That amount is too large");
        return converted;
    }

    /** The rate in force on {@code on}; never guessed: 1 would book $100 as ₹100. */
    private BigDecimal fxRate(String userId, String from, String to, LocalDate on) {
        if (from.equals(to)) return BigDecimal.ONE.setScale(8); // DECIMAL(18,8), as stored
        return rateRepo.findEffectiveRate(userId, from, to, on).map(ExchangeRate::getRate)
                .orElseThrow(() -> ApiException.missingExchangeRate(
                        "Set an exchange rate for " + from + " → " + to + " before recording this transaction"));
    }

    /**
     * What arrives in the destination account. Same currency: the amount sent. Different currencies:
     * the amount the user entered, else converted at the saved rate — never the same number relabelled.
     */
    private BigDecimal received(String userId, BigDecimal amount, FinancialAccount from, FinancialAccount to,
                                BigDecimal entered, LocalDate on) {
        if (from.getCurrency().equals(to.getCurrency())) return amount;
        if (entered != null) return entered.setScale(4);
        BigDecimal rate = rateRepo.findEffectiveRate(userId, from.getCurrency(), to.getCurrency(), on)
                .map(ExchangeRate::getRate)
                .orElseThrow(() -> invalid("transferAmount",
                        "Enter the amount received in " + to.getCurrency()));
        return fits(amount.multiply(rate).setScale(4, RoundingMode.HALF_UP));
    }

    private UserSettings settingsForShare(String userId) {
        return settingsRepo.findForShare(userId).orElseThrow(() -> ApiException.notFound("Settings not found"));
    }

    private FinancialAccount usableAccount(String userId, String accountId, String notFound) {
        FinancialAccount a = accountRepo.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)
                .orElseThrow(() -> ApiException.notFound(notFound));
        if (a.getArchivedAt() != null) throw ApiException.accountArchived("That account is archived");
        return a;
    }

    private Category category(String userId, String categoryId, TransactionType type) {
        Category c = categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(categoryId, userId)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        CategoryKind expected = type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        if (c.getKind() != expected) {
            throw invalid("categoryId", type == TransactionType.INCOME
                    ? "Choose an income category" : "Choose an expense category");
        }
        return c;
    }

    private Transaction transaction(String userId, String id) {
        return txRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("We couldn't find that transaction"));
    }

    private static ApiException invalid(String field, String message) {
        return ApiException.validationFailed(message, Map.of(field, List.of(message)));
    }
}
