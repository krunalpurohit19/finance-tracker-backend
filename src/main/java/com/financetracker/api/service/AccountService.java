package com.financetracker.api.service;

import com.financetracker.api.dto.account.AccountDtos.*;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.enums.AccountClass;
import com.financetracker.api.entity.enums.AccountType;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    /** DECIMAL(18,4) zero: "0.0000", not "0". */
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private final FinancialAccountRepository accountRepo;
    private final TransactionRepository txRepo;
    private final UserRepository userRepo;

    public AccountService(FinancialAccountRepository accountRepo, TransactionRepository txRepo, UserRepository userRepo) {
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(String userId, boolean includeArchived) {
        List<FinancialAccount> accounts = includeArchived
                ? accountRepo.findActiveByUserId(userId)
                : accountRepo.findActiveNonArchivedByUserId(userId);
        return accounts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(String userId, String id) {
        return toResponse(account(userId, id));
    }

    @Transactional
    public AccountResponse create(String userId, CreateAccountRequest req) {
        boolean isDefault = Boolean.TRUE.equals(req.isDefault());
        if (isDefault) clearDefault(userId);
        FinancialAccount account = FinancialAccount.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .name(req.name())
                .type(req.type())
                .accountClass(req.accountClass() != null ? req.accountClass()
                        : req.type() == AccountType.CREDIT_CARD ? AccountClass.LIABILITY : AccountClass.ASSET)
                .currency(req.currency())
                // DECIMAL(18,4); @Digits makes this exact. May be negative (an overdraft, a card balance).
                .openingBalance(req.openingBalance() != null ? req.openingBalance().setScale(4) : ZERO)
                .institution(req.institution())
                .last4(req.last4())
                .color(req.color())
                .icon(req.icon())
                .isDefault(isDefault)
                .build();
        accountRepo.save(account);
        return AccountResponse.of(account, account.getOpeningBalance()); // no transactions yet
    }

    @Transactional
    public AccountResponse update(String userId, String id, UpdateAccountRequest req) {
        FinancialAccount account = account(userId, id);
        if (req.name() != null) account.setName(req.name());
        if (req.type() != null) account.setType(req.type());
        if (req.accountClass() != null) account.setAccountClass(req.accountClass());
        if (req.openingBalance() != null) account.setOpeningBalance(req.openingBalance().setScale(4));
        if (req.institution() != null) account.setInstitution(req.institution());
        if (req.last4() != null) account.setLast4(req.last4());
        if (req.color() != null) account.setColor(req.color());
        if (req.icon() != null) account.setIcon(req.icon());
        if (req.isDefault() != null) {
            if (req.isDefault() && !account.isDefault()) clearDefault(userId);
            account.setDefault(req.isDefault());
        }
        return toResponse(account);
    }

    /** Archiving hides an account from pickers; its history and balance stay. Idempotent. */
    @Transactional
    public AccountResponse archive(String userId, String id) {
        FinancialAccount account = account(userId, id);
        if (account.getArchivedAt() == null) account.setArchivedAt(Instant.now());
        return toResponse(account);
    }

    @Transactional
    public AccountResponse unarchive(String userId, String id) {
        FinancialAccount account = account(userId, id);
        account.setArchivedAt(null);
        return toResponse(account);
    }

    /** Only an account with no transactions can be deleted; otherwise archive it. */
    @Transactional
    public void remove(String userId, String id) {
        FinancialAccount account = account(userId, id);
        if (accountRepo.countTransactionsByAccountId(id) + accountRepo.countTransfersByAccountId(id) > 0) {
            throw ApiException.accountInUse("This account has transactions and cannot be deleted");
        }
        account.setDeletedAt(Instant.now());
    }

    @Transactional
    public Reordered reorder(String userId, List<String> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) account(userId, orderedIds.get(i)).setSortOrder(i);
        return new Reordered(orderedIds.size());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** At most one default account per user. */
    private void clearDefault(String userId) {
        for (FinancialAccount a : accountRepo.findActiveByUserId(userId)) {
            if (a.isDefault()) a.setDefault(false);
        }
    }

    private FinancialAccount account(String userId, String id) {
        return accountRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("We couldn't find that account"));
    }

    // ponytail: one movement SUM per account; a GROUP BY account query if someone has dozens of accounts.
    private AccountResponse toResponse(FinancialAccount a) {
        BigDecimal movement = txRepo.computeAccountMovement(a.getId());
        return AccountResponse.of(a, a.getOpeningBalance().add(movement != null ? movement : ZERO));
    }
}
