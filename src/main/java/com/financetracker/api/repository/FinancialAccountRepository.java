package com.financetracker.api.repository;

import com.financetracker.api.entity.FinancialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, String> {

    @Query("SELECT a FROM FinancialAccount a WHERE a.user.id = :userId AND a.deletedAt IS NULL ORDER BY a.sortOrder")
    List<FinancialAccount> findActiveByUserId(String userId);

    @Query("SELECT a FROM FinancialAccount a WHERE a.user.id = :userId AND a.deletedAt IS NULL AND a.archivedAt IS NULL ORDER BY a.sortOrder")
    List<FinancialAccount> findActiveNonArchivedByUserId(String userId);

    Optional<FinancialAccount> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.id = :accountId AND t.deletedAt IS NULL")
    long countTransactionsByAccountId(String accountId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transferAccount.id = :accountId AND t.deletedAt IS NULL")
    long countTransfersByAccountId(String accountId);
}
