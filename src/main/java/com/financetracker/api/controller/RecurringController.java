package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.*;
import com.financetracker.api.entity.enums.Frequency;
import com.financetracker.api.entity.enums.TransactionSource;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.*;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/recurring")
public class RecurringController {

    private final RecurringTransactionRepository recurringRepo;
    private final FinancialAccountRepository accountRepo;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository txRepo;
    private final ExchangeRateRepository rateRepo;
    private final UserSettingsRepository settingsRepo;

    public RecurringController(RecurringTransactionRepository recurringRepo,
                                FinancialAccountRepository accountRepo, CategoryRepository categoryRepo,
                                TransactionRepository txRepo, ExchangeRateRepository rateRepo,
                                UserSettingsRepository settingsRepo) {
        this.recurringRepo = recurringRepo;
        this.accountRepo = accountRepo;
        this.categoryRepo = categoryRepo;
        this.txRepo = txRepo;
        this.rateRepo = rateRepo;
        this.settingsRepo = settingsRepo;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> list() {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                recurringRepo.findByUserIdAndDeletedAtIsNullOrderByNextOccurrence(SecurityUtils.currentUserId())
                        .stream().map(this::toMap).toList()));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> upcoming() {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                recurringRepo.findUpcoming(SecurityUtils.currentUserId())
                        .stream().map(this::toMap).toList()));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.currentUserId();
        LocalDate startOn = LocalDate.parse((String) body.get("startOn"));

        RecurringTransaction rec = RecurringTransaction.builder()
                .id(UUID.randomUUID().toString())
                .user(User.builder().id(userId).build())
                .name((String) body.get("name"))
                .type(TransactionType.valueOf((String) body.get("type")))
                .amount(new BigDecimal((String) body.get("amount")))
                .currency(((String) body.getOrDefault("currency", "INR")).toUpperCase())
                .account(accountRepo.findByIdAndUserIdAndDeletedAtIsNull((String) body.get("accountId"), userId).orElseThrow())
                .frequency(Frequency.valueOf((String) body.get("frequency")))
                .interval(body.containsKey("interval") ? (int) body.get("interval") : 1)
                .startOn(startOn)
                .endOn(body.containsKey("endOn") ? LocalDate.parse((String) body.get("endOn")) : null)
                .dayOfMonth(body.containsKey("dayOfMonth") ? (Integer) body.get("dayOfMonth") : null)
                .nextOccurrence(startOn)
                .merchant((String) body.get("merchant"))
                .notes((String) body.get("notes"))
                .build();

        if (body.containsKey("transferAccountId")) {
            rec.setTransferAccount(accountRepo.findByIdAndUserIdAndDeletedAtIsNull((String) body.get("transferAccountId"), userId).orElseThrow());
        }
        if (body.containsKey("categoryId")) {
            rec.setCategory(categoryRepo.findByIdAndUserIdAndDeletedAtIsNull((String) body.get("categoryId"), userId).orElse(null));
        }

        recurringRepo.save(rec);
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(toMap(rec)));
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> update(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.currentUserId();
        RecurringTransaction rec = findOrThrow(userId, id);
        if (body.containsKey("name")) rec.setName((String) body.get("name"));
        if (body.containsKey("amount")) rec.setAmount(new BigDecimal((String) body.get("amount")));
        if (body.containsKey("isActive")) rec.setActive(Boolean.TRUE.equals(body.get("isActive")));
        if (body.containsKey("endOn")) rec.setEndOn(body.get("endOn") != null ? LocalDate.parse((String) body.get("endOn")) : null);
        recurringRepo.save(rec);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(toMap(rec)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> remove(@PathVariable String id) {
        RecurringTransaction rec = findOrThrow(SecurityUtils.currentUserId(), id);
        rec.setDeletedAt(Instant.now());
        recurringRepo.save(rec);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("id", id)));
    }

    @PostMapping("/generate")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> generate() {
        String userId = SecurityUtils.currentUserId();
        LocalDate today = LocalDate.now();
        List<RecurringTransaction> due = recurringRepo.findDue(userId, today);
        UserSettings settings = settingsRepo.findById(userId).orElseThrow();
        int created = 0;

        for (RecurringTransaction rec : due) {
            LocalDate occurrence = rec.getNextOccurrence();
            while (!occurrence.isAfter(today) && created < 500) {
                if (txRepo.findByRecurringIdAndOccurrenceOn(rec.getId(), occurrence).isEmpty()) {
                    BigDecimal fxRate = rec.getCurrency().equals(settings.getBaseCurrency())
                            ? BigDecimal.ONE
                            : rateRepo.findEffectiveRate(userId, rec.getCurrency(), settings.getBaseCurrency(), occurrence)
                                .map(ExchangeRate::getRate).orElse(BigDecimal.ONE);

                    Transaction tx = Transaction.builder()
                            .id(UUID.randomUUID().toString())
                            .user(User.builder().id(userId).build())
                            .type(rec.getType())
                            .amount(rec.getAmount())
                            .currency(rec.getCurrency())
                            .baseAmount(rec.getAmount().multiply(fxRate).setScale(4, RoundingMode.HALF_UP))
                            .baseCurrency(settings.getBaseCurrency())
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
                            .build();
                    txRepo.save(tx);
                    created++;
                }
                rec.setLastGenerated(occurrence);
                occurrence = advanceOccurrence(rec, occurrence);
                if (rec.getEndOn() != null && occurrence.isAfter(rec.getEndOn())) {
                    rec.setActive(false);
                    break;
                }
            }
            rec.setNextOccurrence(occurrence);
            recurringRepo.save(rec);
        }
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("generated", created)));
    }

    private LocalDate advanceOccurrence(RecurringTransaction rec, LocalDate current) {
        return switch (rec.getFrequency()) {
            case DAILY -> current.plusDays(rec.getInterval());
            case WEEKLY -> current.plusWeeks(rec.getInterval());
            case MONTHLY -> {
                LocalDate next = current.plusMonths(rec.getInterval());
                if (rec.getDayOfMonth() != null) {
                    next = next.withDayOfMonth(Math.min(rec.getDayOfMonth(), next.lengthOfMonth()));
                }
                yield next;
            }
            case YEARLY -> current.plusYears(rec.getInterval());
        };
    }

    private RecurringTransaction findOrThrow(String userId, String id) {
        return recurringRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Recurring transaction not found"));
    }

    private Map<String, Object> toMap(RecurringTransaction r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("type", r.getType().name());
        m.put("amount", r.getAmount().toPlainString());
        m.put("currency", r.getCurrency());
        m.put("accountId", r.getAccount().getId());
        m.put("transferAccountId", r.getTransferAccount() != null ? r.getTransferAccount().getId() : null);
        m.put("categoryId", r.getCategory() != null ? r.getCategory().getId() : null);
        m.put("merchant", r.getMerchant());
        m.put("frequency", r.getFrequency().name());
        m.put("interval", r.getInterval());
        m.put("startOn", r.getStartOn().toString());
        m.put("endOn", r.getEndOn() != null ? r.getEndOn().toString() : null);
        m.put("dayOfMonth", r.getDayOfMonth());
        m.put("nextOccurrence", r.getNextOccurrence().toString());
        m.put("isActive", r.isActive());
        m.put("autoPost", r.isAutoPost());
        return m;
    }
}
