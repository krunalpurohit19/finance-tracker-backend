package com.financetracker.api.service;

import com.financetracker.api.entity.*;
import com.financetracker.api.entity.enums.TransactionSource;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class TransactionService {

    private final TransactionRepository txRepo;
    private final FinancialAccountRepository accountRepo;
    private final CategoryRepository categoryRepo;
    private final ExchangeRateRepository rateRepo;
    private final UserSettingsRepository settingsRepo;

    public TransactionService(TransactionRepository txRepo, FinancialAccountRepository accountRepo,
                               CategoryRepository categoryRepo, ExchangeRateRepository rateRepo,
                               UserSettingsRepository settingsRepo) {
        this.txRepo = txRepo;
        this.accountRepo = accountRepo;
        this.categoryRepo = categoryRepo;
        this.rateRepo = rateRepo;
        this.settingsRepo = settingsRepo;
    }

    public Map<String, Object> list(String userId, Map<String, String> query) {
        TransactionType type = query.containsKey("type") ? TransactionType.valueOf(query.get("type")) : null;
        String accountId = query.get("accountId");
        String categoryId = query.get("categoryId");
        LocalDate from = query.containsKey("from") ? LocalDate.parse(query.get("from")) : null;
        LocalDate to = query.containsKey("to") ? LocalDate.parse(query.get("to")) : null;
        int limit = query.containsKey("limit") ? Integer.parseInt(query.get("limit")) : 50;

        List<Transaction> txs = txRepo.findFiltered(userId, type, accountId, categoryId, from, to,
                PageRequest.of(0, limit + 1));

        boolean hasMore = txs.size() > limit;
        List<Transaction> page = hasMore ? txs.subList(0, limit) : txs;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", page.stream().map(this::toMap).toList());
        if (hasMore) {
            result.put("nextCursor", page.getLast().getId());
        }
        return result;
    }

    public Map<String, Object> get(String userId, String id) {
        return toMap(findOrThrow(userId, id));
    }

    @Transactional
    public Map<String, Object> create(String userId, Map<String, Object> input) {
        TransactionType type = TransactionType.valueOf((String) input.get("type"));
        String accountId = (String) input.get("accountId");
        FinancialAccount account = accountRepo.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));

        if (account.getArchivedAt() != null) {
            throw ApiException.accountArchived("That account is archived");
        }

        UserSettings settings = settingsRepo.findById(userId).orElseThrow();
        BigDecimal amount = new BigDecimal((String) input.get("amount"));
        String currency = account.getCurrency();
        BigDecimal fxRate = resolveFxRate(userId, currency, settings.getBaseCurrency(),
                (String) input.get("fxRate"), LocalDate.parse((String) input.get("occurredOn")));
        BigDecimal baseAmount = amount.multiply(fxRate).setScale(4, RoundingMode.HALF_UP);

        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .user(User.builder().id(userId).build())
                .type(type)
                .amount(amount)
                .currency(currency)
                .baseAmount(baseAmount)
                .baseCurrency(settings.getBaseCurrency())
                .fxRate(fxRate)
                .account(account)
                .occurredOn(LocalDate.parse((String) input.get("occurredOn")))
                .merchant((String) input.get("merchant"))
                .notes((String) input.get("notes"))
                .source(TransactionSource.MANUAL)
                .build();

        if (type == TransactionType.TRANSFER) {
            String transferAccountId = (String) input.get("transferAccountId");
            if (accountId.equals(transferAccountId)) throw ApiException.sameAccountTransfer();
            FinancialAccount transferAccount = accountRepo.findByIdAndUserIdAndDeletedAtIsNull(transferAccountId, userId)
                    .orElseThrow(() -> ApiException.notFound("Transfer account not found"));
            tx.setTransferAccount(transferAccount);
            tx.setTransferCurrency(transferAccount.getCurrency());
            if (input.containsKey("transferAmount")) {
                tx.setTransferAmount(new BigDecimal((String) input.get("transferAmount")));
            } else {
                tx.setTransferAmount(amount);
            }
        } else {
            String categoryId = (String) input.get("categoryId");
            if (categoryId != null) {
                Category cat = categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(categoryId, userId)
                        .orElseThrow(() -> ApiException.notFound("Category not found"));
                tx.setCategory(cat);
            }
        }

        txRepo.save(tx);
        return toMap(tx);
    }

    @Transactional
    public Map<String, Object> update(String userId, String id, Map<String, Object> input) {
        Transaction tx = findOrThrow(userId, id);

        if (input.containsKey("amount")) tx.setAmount(new BigDecimal((String) input.get("amount")));
        if (input.containsKey("occurredOn")) tx.setOccurredOn(LocalDate.parse((String) input.get("occurredOn")));
        if (input.containsKey("merchant")) tx.setMerchant((String) input.get("merchant"));
        if (input.containsKey("notes")) tx.setNotes((String) input.get("notes"));
        if (input.containsKey("categoryId")) {
            String catId = (String) input.get("categoryId");
            tx.setCategory(catId != null ? categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(catId, userId).orElse(null) : null);
        }

        // Recompute base amount if amount or fxRate changed
        UserSettings settings = settingsRepo.findById(userId).orElseThrow();
        BigDecimal fxRate = resolveFxRate(userId, tx.getCurrency(), settings.getBaseCurrency(),
                (String) input.get("fxRate"), tx.getOccurredOn());
        tx.setFxRate(fxRate);
        tx.setBaseAmount(tx.getAmount().multiply(fxRate).setScale(4, RoundingMode.HALF_UP));

        txRepo.save(tx);
        return toMap(tx);
    }

    @Transactional
    public Map<String, Object> remove(String userId, String id) {
        Transaction tx = findOrThrow(userId, id);
        tx.setDeletedAt(Instant.now());
        txRepo.save(tx);
        return Map.of("id", id);
    }

    @Transactional
    public Map<String, Object> duplicate(String userId, String id, String occurredOn) {
        Transaction src = findOrThrow(userId, id);
        Transaction dup = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .user(src.getUser())
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
                .category(src.getCategory())
                .occurredOn(occurredOn != null ? LocalDate.parse(occurredOn) : src.getOccurredOn())
                .merchant(src.getMerchant())
                .notes(src.getNotes())
                .source(TransactionSource.MANUAL)
                .build();
        txRepo.save(dup);
        return toMap(dup);
    }

    private Transaction findOrThrow(String userId, String id) {
        return txRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("We couldn't find that transaction"));
    }

    private BigDecimal resolveFxRate(String userId, String fromCurrency, String baseCurrency,
                                     String explicitRate, LocalDate asOf) {
        if (explicitRate != null) return new BigDecimal(explicitRate);
        if (fromCurrency.equals(baseCurrency)) return BigDecimal.ONE;

        return rateRepo.findEffectiveRate(userId, fromCurrency, baseCurrency, asOf)
                .map(ExchangeRate::getRate)
                .orElseThrow(() -> ApiException.missingExchangeRate(
                        "Set an exchange rate for " + fromCurrency + " → " + baseCurrency + " before recording this transaction"));
    }

    private Map<String, Object> toMap(Transaction tx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tx.getId());
        m.put("type", tx.getType().name());
        m.put("amount", tx.getAmount().toPlainString());
        m.put("currency", tx.getCurrency());
        m.put("baseAmount", tx.getBaseAmount().toPlainString());
        m.put("baseCurrency", tx.getBaseCurrency());
        m.put("fxRate", tx.getFxRate().toPlainString());
        m.put("accountId", tx.getAccount().getId());
        m.put("transferAccountId", tx.getTransferAccount() != null ? tx.getTransferAccount().getId() : null);
        m.put("transferAmount", tx.getTransferAmount() != null ? tx.getTransferAmount().toPlainString() : null);
        m.put("transferCurrency", tx.getTransferCurrency());
        m.put("categoryId", tx.getCategory() != null ? tx.getCategory().getId() : null);
        m.put("occurredOn", tx.getOccurredOn().toString());
        m.put("merchant", tx.getMerchant());
        m.put("notes", tx.getNotes());
        m.put("source", tx.getSource().name());
        m.put("recurringId", tx.getRecurring() != null ? tx.getRecurring().getId() : null);
        m.put("createdAt", tx.getCreatedAt());
        return m;
    }
}
