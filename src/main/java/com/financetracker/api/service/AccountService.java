package com.financetracker.api.service;

import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.User;
import com.financetracker.api.entity.enums.AccountClass;
import com.financetracker.api.entity.enums.AccountType;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class AccountService {

    private final FinancialAccountRepository accountRepo;
    private final TransactionRepository txRepo;

    public AccountService(FinancialAccountRepository accountRepo, TransactionRepository txRepo) {
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
    }

    public List<Map<String, Object>> list(String userId, boolean includeArchived) {
        List<FinancialAccount> accounts = includeArchived
                ? accountRepo.findActiveByUserId(userId)
                : accountRepo.findActiveNonArchivedByUserId(userId);

        return accounts.stream().map(a -> toMap(a, true)).toList();
    }

    public Map<String, Object> get(String userId, String id) {
        FinancialAccount account = findOrThrow(userId, id);
        return toMap(account, true);
    }

    @Transactional
    public Map<String, Object> create(String userId, Map<String, Object> input) {
        String id = UUID.randomUUID().toString();
        AccountType type = AccountType.valueOf((String) input.get("type"));
        AccountClass accountClass = input.containsKey("class")
                ? AccountClass.valueOf((String) input.get("class"))
                : (type == AccountType.CREDIT_CARD ? AccountClass.LIABILITY : AccountClass.ASSET);

        FinancialAccount account = FinancialAccount.builder()
                .id(id)
                .user(User.builder().id(userId).build())
                .name((String) input.get("name"))
                .type(type)
                .accountClass(accountClass)
                .currency(((String) input.get("currency")).toUpperCase())
                .openingBalance(new BigDecimal(input.getOrDefault("openingBalance", "0").toString()))
                .institution((String) input.get("institution"))
                .last4((String) input.get("last4"))
                .color((String) input.get("color"))
                .icon((String) input.get("icon"))
                .isDefault(Boolean.TRUE.equals(input.get("isDefault")))
                .build();

        accountRepo.save(account);
        return toMap(account, true);
    }

    @Transactional
    public Map<String, Object> update(String userId, String id, Map<String, Object> input) {
        FinancialAccount account = findOrThrow(userId, id);

        if (input.containsKey("name")) account.setName((String) input.get("name"));
        if (input.containsKey("type")) account.setType(AccountType.valueOf((String) input.get("type")));
        if (input.containsKey("class")) account.setAccountClass(AccountClass.valueOf((String) input.get("class")));
        if (input.containsKey("openingBalance")) account.setOpeningBalance(new BigDecimal(input.get("openingBalance").toString()));
        if (input.containsKey("institution")) account.setInstitution((String) input.get("institution"));
        if (input.containsKey("last4")) account.setLast4((String) input.get("last4"));
        if (input.containsKey("color")) account.setColor((String) input.get("color"));
        if (input.containsKey("icon")) account.setIcon((String) input.get("icon"));
        if (input.containsKey("isDefault")) account.setDefault(Boolean.TRUE.equals(input.get("isDefault")));

        accountRepo.save(account);
        return toMap(account, true);
    }

    @Transactional
    public Map<String, Object> archive(String userId, String id) {
        FinancialAccount account = findOrThrow(userId, id);
        account.setArchivedAt(Instant.now());
        accountRepo.save(account);
        return toMap(account, false);
    }

    @Transactional
    public Map<String, Object> unarchive(String userId, String id) {
        FinancialAccount account = findOrThrow(userId, id);
        account.setArchivedAt(null);
        accountRepo.save(account);
        return toMap(account, false);
    }

    @Transactional
    public Map<String, Object> remove(String userId, String id) {
        FinancialAccount account = findOrThrow(userId, id);
        long txCount = accountRepo.countTransactionsByAccountId(id) + accountRepo.countTransfersByAccountId(id);
        if (txCount > 0) {
            throw ApiException.accountInUse("This account has transactions and cannot be deleted");
        }
        account.setDeletedAt(Instant.now());
        accountRepo.save(account);
        return Map.of("id", id);
    }

    @Transactional
    public Map<String, Object> reorder(String userId, List<String> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            FinancialAccount account = findOrThrow(userId, orderedIds.get(i));
            account.setSortOrder(i);
            accountRepo.save(account);
        }
        return Map.of("reordered", orderedIds.size());
    }

    private FinancialAccount findOrThrow(String userId, String id) {
        return accountRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("We couldn't find that account"));
    }

    private Map<String, Object> toMap(FinancialAccount a, boolean includeBalance) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("type", a.getType().name());
        m.put("class", a.getAccountClass().name());
        m.put("currency", a.getCurrency());
        m.put("openingBalance", a.getOpeningBalance().toPlainString());
        m.put("institution", a.getInstitution());
        m.put("last4", a.getLast4());
        m.put("color", a.getColor());
        m.put("icon", a.getIcon());
        m.put("isDefault", a.isDefault());
        m.put("sortOrder", a.getSortOrder());
        m.put("archivedAt", a.getArchivedAt());
        m.put("createdAt", a.getCreatedAt());
        if (includeBalance) {
            BigDecimal movement = txRepo.computeAccountMovement(a.getId());
            m.put("balance", a.getOpeningBalance().add(movement != null ? movement : BigDecimal.ZERO).toPlainString());
        }
        return m;
    }
}
