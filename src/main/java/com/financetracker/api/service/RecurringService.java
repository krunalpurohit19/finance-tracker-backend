package com.financetracker.api.service;

import com.financetracker.api.dto.recurring.RecurringDtos.*;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.RecurringTransaction;
import com.financetracker.api.entity.Transaction;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.TransactionSource;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.RecurringTransactionRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class RecurringService {

    /** Most transactions one generate call may create. */
    static final int MAX_PER_RUN = 500;

    private final RecurringTransactionRepository recurringRepo;
    private final FinancialAccountRepository accountRepo;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository txRepo;
    private final ExchangeRateRepository rateRepo;
    private final UserSettingsRepository settingsRepo;
    private final UserRepository userRepo;

    public RecurringService(RecurringTransactionRepository recurringRepo, FinancialAccountRepository accountRepo,
                            CategoryRepository categoryRepo, TransactionRepository txRepo,
                            ExchangeRateRepository rateRepo, UserSettingsRepository settingsRepo,
                            UserRepository userRepo) {
        this.recurringRepo = recurringRepo;
        this.accountRepo = accountRepo;
        this.categoryRepo = categoryRepo;
        this.txRepo = txRepo;
        this.rateRepo = rateRepo;
        this.settingsRepo = settingsRepo;
        this.userRepo = userRepo;
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

    @Transactional
    public RecurringResponse create(String userId, CreateRecurringRequest req) {
        RecurringTransaction rec = RecurringTransaction.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .name(req.name())
                .type(req.type())
                .amount(req.amount())
                .currency(req.currency() != null ? req.currency() : "INR")
                .account(accountRepo.findByIdAndUserIdAndDeletedAtIsNull(req.accountId(), userId)
                        .orElseThrow(() -> ApiException.notFound("Account not found")))
                .frequency(req.frequency())
                .interval(req.interval() != null ? req.interval() : 1)
                .startOn(req.startOn())
                .endOn(req.endOn())
                .dayOfMonth(req.dayOfMonth())
                .nextOccurrence(req.startOn())
                .merchant(req.merchant())
                .notes(req.notes())
                .build();
        if (req.transferAccountId() != null) {
            rec.setTransferAccount(accountRepo.findByIdAndUserIdAndDeletedAtIsNull(req.transferAccountId(), userId)
                    .orElseThrow(() -> ApiException.notFound("Transfer account not found")));
        }
        if (req.categoryId() != null) {
            rec.setCategory(categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(req.categoryId(), userId).orElse(null));
        }
        recurringRepo.save(rec);
        return RecurringResponse.of(rec);
    }

    @Transactional
    public RecurringResponse update(String userId, String id, UpdateRecurringRequest req) {
        RecurringTransaction rec = rule(userId, id);
        if (req.getName() != null) rec.setName(req.getName());
        if (req.getAmount() != null) rec.setAmount(req.getAmount());
        if (req.getIsActive() != null) rec.setActive(req.getIsActive());
        if (req.getEndOn() != null) rec.setEndOn(req.getEndOn().orElse(null));
        return RecurringResponse.of(rec);
    }

    @Transactional
    public void remove(String userId, String id) {
        rule(userId, id).setDeletedAt(Instant.now());
    }

    /** Materialise every occurrence that has fallen due, up to MAX_PER_RUN transactions. Idempotent. */
    @Transactional
    public GenerationResult generate(String userId) {
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> due = recurringRepo.findDue(userId, today);
        UserSettings settings = settingsRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Settings not found"));
        String base = settings.getBaseCurrency();
        int created = 0;

        for (RecurringTransaction rec : due) {
            LocalDate occurrence = rec.getNextOccurrence();
            while (!occurrence.isAfter(today) && created < MAX_PER_RUN) {
                if (txRepo.findByRecurringIdAndOccurrenceOn(rec.getId(), occurrence).isEmpty()) {
                    BigDecimal fxRate = rec.getCurrency().equals(base)
                            ? BigDecimal.ONE
                            : rateRepo.findEffectiveRate(userId, rec.getCurrency(), base, occurrence)
                                .map(ExchangeRate::getRate).orElse(BigDecimal.ONE);
                    txRepo.save(Transaction.builder()
                            .id(UUID.randomUUID().toString())
                            .user(userRepo.getReferenceById(userId))
                            .type(rec.getType())
                            .amount(rec.getAmount())
                            .currency(rec.getCurrency())
                            .baseAmount(rec.getAmount().multiply(fxRate).setScale(4, RoundingMode.HALF_UP))
                            .baseCurrency(base)
                            .fxRate(fxRate)
                            .account(rec.getAccount())
                            .transferAccount(rec.getTransferAccount())
                            .category(rec.getCategory())
                            .occurredOn(occurrence)
                            .merchant(rec.getMerchant())
                            .notes(rec.getNotes())
                            .source(TransactionSource.MANUAL)
                            .recurring(rec)
                            .occurrenceOn(occurrence)
                            .build());
                    created++;
                }
                rec.setLastGenerated(occurrence);
                occurrence = next(rec, occurrence);
                if (rec.getEndOn() != null && occurrence.isAfter(rec.getEndOn())) {
                    rec.setActive(false);
                    break;
                }
            }
            rec.setNextOccurrence(occurrence);
        }
        return new GenerationResult(created);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    static LocalDate next(RecurringTransaction rec, LocalDate current) {
        return switch (rec.getFrequency()) {
            case DAILY -> current.plusDays(rec.getInterval());
            case WEEKLY -> current.plusWeeks(rec.getInterval());
            case MONTHLY -> {
                LocalDate next = current.plusMonths(rec.getInterval());
                yield rec.getDayOfMonth() != null
                        ? next.withDayOfMonth(Math.min(rec.getDayOfMonth(), next.lengthOfMonth()))
                        : next;
            }
            case YEARLY -> current.plusYears(rec.getInterval());
        };
    }

    private RecurringTransaction rule(String userId, String id) {
        return recurringRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Recurring transaction not found"));
    }
}
