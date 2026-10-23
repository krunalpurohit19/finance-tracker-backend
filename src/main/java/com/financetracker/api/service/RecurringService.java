package com.financetracker.api.service;

import com.financetracker.api.dto.recurring.RecurringDtos.*;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.RecurringTransaction;
import com.financetracker.api.entity.Transaction;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.entity.enums.Frequency;
import com.financetracker.api.entity.enums.TransactionSource;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.RecurringTransactionRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecurringService {

    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);

    /** Most transactions one generate call may create. */
    static final int MAX_PER_RUN = 500;

    private final RecurringTransactionRepository recurringRepo;
    private final FinancialAccountRepository accountRepo;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository txRepo;
    private final ExchangeRateRepository rateRepo;
    private final UserSettingsRepository settingsRepo;
    private final UserRepository userRepo;
    private final Clock clock;

    public RecurringService(RecurringTransactionRepository recurringRepo, FinancialAccountRepository accountRepo,
                            CategoryRepository categoryRepo, TransactionRepository txRepo,
                            ExchangeRateRepository rateRepo, UserSettingsRepository settingsRepo,
                            UserRepository userRepo, Clock clock) {
        this.recurringRepo = recurringRepo;
        this.accountRepo = accountRepo;
        this.categoryRepo = categoryRepo;
        this.txRepo = txRepo;
        this.rateRepo = rateRepo;
        this.settingsRepo = settingsRepo;
        this.userRepo = userRepo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RecurringResponse> list(String userId) {
        return recurringRepo.findByUserIdAndDeletedAtIsNullOrderByNextOccurrence(userId).stream()
                .map(RecurringResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public List<RecurringResponse> listUpcoming(String userId) {
        return recurringRepo.findUpcoming(userId).stream().map(RecurringResponse::of).toList();
    }

    /**
     * A rule posts in its account's currency. A transfer moves the same amount between two accounts of
     * that currency: recurring rules have no transferAmount, so a cross-currency transfer can't be one.
     */
    @Transactional
    public RecurringResponse create(String userId, CreateRecurringRequest req) {
        FinancialAccount account = usableAccount(userId, req.accountId(), "Account not found");
        if (req.currency() != null && !req.currency().equals(account.getCurrency())) {
            throw ApiException.currencyMismatch("A recurring transaction uses its account's currency ("
                    + account.getCurrency() + ")");
        }
        if (req.endOn() != null && req.endOn().isBefore(req.startOn())) {
            throw invalid("endOn", "The end date can't be before the start date");
        }
        RecurringTransaction rec = RecurringTransaction.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .name(req.name())
                .type(req.type())
                .amount(req.amount().setScale(4)) // DECIMAL(18,4); @Digits makes this exact
                .currency(account.getCurrency())
                .account(account)
                .frequency(req.frequency())
                .interval(req.interval() != null ? req.interval() : 1)
                .startOn(req.startOn())
                .endOn(req.endOn())
                // Monthly rules keep their day: 31st stays the 31st (or the month's last day), never drifts.
                .dayOfMonth(req.dayOfMonth() != null ? req.dayOfMonth()
                        : req.frequency() == Frequency.MONTHLY ? req.startOn().getDayOfMonth() : null)
                .nextOccurrence(req.startOn())
                .merchant(req.merchant())
                .notes(req.notes())
                .build();

        if (req.type() == TransactionType.TRANSFER) {
            if (req.transferAccountId() == null) throw invalid("transferAccountId", "Choose a destination account");
            if (req.transferAccountId().equals(req.accountId())) throw ApiException.sameAccountTransfer();
            FinancialAccount to = usableAccount(userId, req.transferAccountId(), "Transfer account not found");
            if (!to.getCurrency().equals(account.getCurrency())) {
                throw ApiException.currencyMismatch("Recurring transfers must be between accounts in the same currency");
            }
            rec.setTransferAccount(to);
        } else {
            if (req.transferAccountId() != null) {
                throw invalid("transferAccountId", "Only transfers have a destination account");
            }
            if (req.categoryId() != null) rec.setCategory(category(userId, req.categoryId(), req.type()));
        }
        recurringRepo.save(rec);
        return RecurringResponse.of(rec);
    }

    @Transactional
    public RecurringResponse update(String userId, String id, UpdateRecurringRequest req) {
        RecurringTransaction rec = rule(userId, id);
        if (req.getName() != null) rec.setName(req.getName());
        if (req.getAmount() != null) rec.setAmount(req.getAmount().setScale(4));
        if (req.getMerchant() != null) rec.setMerchant(req.getMerchant());
        if (req.getNotes() != null) rec.setNotes(req.getNotes());
        if (req.getCategoryId() != null) {
            if (rec.getType() == TransactionType.TRANSFER && req.getCategoryId().isPresent()) {
                throw invalid("categoryId", "Transfers don't have a category");
            }
            rec.setCategory(req.getCategoryId().map(c -> category(userId, c, rec.getType())).orElse(null));
        }
        if (req.getEndOn() != null) {
            LocalDate endOn = req.getEndOn().orElse(null);
            if (endOn != null && endOn.isBefore(rec.getStartOn())) {
                throw invalid("endOn", "The end date can't be before the start date");
            }
            rec.setEndOn(endOn);
        }
        if (req.getIsActive() != null) {
            if (req.getIsActive() && !rec.isActive()) resume(rec, userId);
            rec.setActive(req.getIsActive());
        }
        return RecurringResponse.of(rec);
    }

    @Transactional
    public void remove(String userId, String id) {
        rule(userId, id).setDeletedAt(Instant.now());
    }

    /**
     * Materialise every occurrence that has fallen due by the user's today, up to MAX_PER_RUN
     * transactions. Idempotent (uk_tx_recurring_occurrence). A rule that can't be posted correctly is
     * held back — not advanced — and counted as skipped, so it catches up once fixed.
     */
    @Transactional
    public GenerationResult generate(String userId) {
        UserSettings settings = settingsRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Settings not found"));
        LocalDate today = UserDates.today(clock, settings);
        List<RecurringTransaction> due = recurringRepo.findDue(userId, today); // row-locked: see findDue
        String base = settings.getBaseCurrency();
        int created = 0;
        int skipped = 0;

        for (RecurringTransaction rec : due) {
            if (!postable(rec)) {
                skipped++;
                continue;
            }
            LocalDate occurrence = rec.getNextOccurrence();
            LocalDate endOn = rec.getEndOn();
            boolean heldBack = false;
            while (!occurrence.isAfter(today) && (endOn == null || !occurrence.isAfter(endOn)) && created < MAX_PER_RUN) {
                if (txRepo.findByRecurringIdAndOccurrenceOn(rec.getId(), occurrence).isEmpty()) {
                    Optional<BigDecimal> fxRate = fxRate(userId, rec.getCurrency(), base, occurrence);
                    if (fxRate.isEmpty()) {
                        // Never guess a rate: 1 would book $100 as ₹100. Resume from here next time.
                        log.info("Recurring {} held back at {}: no {}→{} rate", rec.getId(), occurrence, rec.getCurrency(), base);
                        heldBack = true;
                        break;
                    }
                    txRepo.save(transaction(rec, occurrence, fxRate.get(), base, userId));
                    created++;
                }
                rec.setLastGenerated(occurrence);
                occurrence = next(rec, occurrence);
            }
            if (heldBack) skipped++;
            rec.setNextOccurrence(occurrence);
            if (endOn != null && occurrence.isAfter(endOn)) rec.setActive(false);
        }
        return new GenerationResult(created, created, skipped, due.size());
    }

    // ── generation helpers ───────────────────────────────────────────────

    /** Posting into an archived/deleted account, or in a currency that isn't the account's, would be wrong. */
    private static boolean postable(RecurringTransaction rec) {
        FinancialAccount from = rec.getAccount();
        if (!usable(from) || !from.getCurrency().equals(rec.getCurrency())) return false;
        if (rec.getType() != TransactionType.TRANSFER) return true;
        FinancialAccount to = rec.getTransferAccount();
        return to != null && usable(to) && to.getCurrency().equals(from.getCurrency());
    }

    private static boolean usable(FinancialAccount a) {
        return a.getDeletedAt() == null && a.getArchivedAt() == null;
    }

    private Optional<BigDecimal> fxRate(String userId, String currency, String base, LocalDate on) {
        if (currency.equals(base)) return Optional.of(BigDecimal.ONE);
        return rateRepo.findEffectiveRate(userId, currency, base, on).map(ExchangeRate::getRate);
    }

    /** One row per occurrence; a transfer is one TRANSFER row with both accounts, like a manual one. */
    private Transaction transaction(RecurringTransaction rec, LocalDate on, BigDecimal fxRate, String base, String userId) {
        boolean transfer = rec.getType() == TransactionType.TRANSFER;
        Category category = transfer || rec.getCategory() == null || rec.getCategory().getDeletedAt() != null
                ? null : rec.getCategory();
        return Transaction.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .type(rec.getType())
                .amount(rec.getAmount())
                .currency(rec.getCurrency())
                .baseAmount(rec.getAmount().multiply(fxRate).setScale(4, RoundingMode.HALF_UP))
                .baseCurrency(base)
                .fxRate(fxRate)
                .account(rec.getAccount())
                .transferAccount(transfer ? rec.getTransferAccount() : null)
                .transferAmount(transfer ? rec.getAmount() : null)
                .transferCurrency(transfer ? rec.getTransferAccount().getCurrency() : null)
                .category(category)
                .occurredOn(on)
                .merchant(rec.getMerchant())
                .notes(rec.getNotes())
                .source(TransactionSource.MANUAL)
                .recurring(rec)
                .occurrenceOn(on)
                .build();
    }

    /**
     * The occurrence after {@code current}. Monthly and yearly dates are anchored on the rule's day
     * (dayOfMonth, else startOn's), clamped to short months, so Jan 31 → Feb 28 → Mar 31 and a
     * Feb 29 start comes back on Feb 29 in leap years. Guards legacy rows: interval < 1 counts as 1.
     */
    static LocalDate next(RecurringTransaction rec, LocalDate current) {
        int interval = Math.max(1, rec.getInterval());
        return switch (rec.getFrequency()) {
            case DAILY -> current.plusDays(interval);
            case WEEKLY -> current.plusWeeks(interval);
            case MONTHLY -> {
                LocalDate next = current.plusMonths(interval);
                int day = rec.getDayOfMonth() != null ? rec.getDayOfMonth() : rec.getStartOn().getDayOfMonth();
                yield next.withDayOfMonth(Math.max(1, Math.min(day, next.lengthOfMonth())));
            }
            case YEARLY -> {
                LocalDate next = current.plusYears(interval).withMonth(rec.getStartOn().getMonthValue());
                yield next.withDayOfMonth(Math.min(rec.getStartOn().getDayOfMonth(), next.lengthOfMonth()));
            }
        };
    }

    /** Pausing means "don't post": on resume, skip the occurrences that fell due meanwhile. */
    private void resume(RecurringTransaction rec, String userId) {
        LocalDate today = settingsRepo.findById(userId).map(s -> UserDates.today(clock, s)).orElse(LocalDate.now(clock));
        LocalDate occurrence = rec.getNextOccurrence();
        while (occurrence.isBefore(today)) occurrence = next(rec, occurrence);
        rec.setNextOccurrence(occurrence);
    }

    // ── lookups ──────────────────────────────────────────────────────────

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

    private RecurringTransaction rule(String userId, String id) {
        return recurringRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Recurring transaction not found"));
    }

    private static ApiException invalid(String field, String message) {
        return ApiException.validationFailed(message, Map.of(field, List.of(message)));
    }
}
