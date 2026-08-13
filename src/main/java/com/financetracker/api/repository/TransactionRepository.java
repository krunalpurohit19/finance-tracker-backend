package com.financetracker.api.repository;

import com.financetracker.api.entity.Transaction;
import com.financetracker.api.entity.enums.TransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND (:type IS NULL OR t.type = :type)
          AND (:accountId IS NULL OR t.account.id = :accountId OR t.transferAccount.id = :accountId)
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:from IS NULL OR t.occurredOn >= :from)
          AND (:to IS NULL OR t.occurredOn <= :to)
        ORDER BY t.occurredOn DESC, t.createdAt DESC
    """)
    List<Transaction> findFiltered(String userId, TransactionType type, String accountId,
                                   String categoryId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.baseAmount ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.baseAmount ELSE 0 END), 0)
        FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.occurredOn >= :from AND t.occurredOn <= :to
    """)
    List<Object[]> sumIncomeAndExpense(String userId, LocalDate from, LocalDate to);

    @Query("""
        SELECT t.category.id, COALESCE(SUM(t.baseAmount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId AND t.type = :type AND t.deletedAt IS NULL
          AND t.occurredOn >= :from AND t.occurredOn <= :to
        GROUP BY t.category.id
    """)
    List<Object[]> sumByCategory(String userId, TransactionType type, LocalDate from, LocalDate to);

    @Query("""
        SELECT FUNCTION('DATE_FORMAT', t.occurredOn, '%Y-%m'),
               COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.baseAmount ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.baseAmount ELSE 0 END), 0)
        FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.occurredOn >= :from AND t.occurredOn <= :to
        GROUP BY FUNCTION('DATE_FORMAT', t.occurredOn, '%Y-%m')
        ORDER BY FUNCTION('DATE_FORMAT', t.occurredOn, '%Y-%m')
    """)
    List<Object[]> monthlyTrend(String userId, LocalDate from, LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(t.baseAmount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.deletedAt IS NULL
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND t.occurredOn >= :from AND t.occurredOn <= :to
    """)
    BigDecimal sumExpenseForBudget(String userId, String categoryId, LocalDate from, LocalDate to);

    @Query("SELECT t FROM Transaction t WHERE t.recurring.id = :recurringId AND t.occurrenceOn = :occurrenceOn")
    Optional<Transaction> findByRecurringIdAndOccurrenceOn(String recurringId, LocalDate occurrenceOn);

    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN t.type = 'INCOME' AND t.account.id = :accountId THEN t.amount
                 WHEN t.type = 'EXPENSE' AND t.account.id = :accountId THEN -t.amount
                 WHEN t.type = 'TRANSFER' AND t.account.id = :accountId THEN -t.amount
                 WHEN t.type = 'TRANSFER' AND t.transferAccount.id = :accountId THEN COALESCE(t.transferAmount, t.amount)
                 ELSE 0 END
        ), 0)
        FROM Transaction t
        WHERE (t.account.id = :accountId OR t.transferAccount.id = :accountId)
          AND t.deletedAt IS NULL
    """)
    BigDecimal computeAccountMovement(String accountId);

    @Query("""
        SELECT t.merchant, COALESCE(SUM(t.baseAmount), 0), COUNT(t)
        FROM Transaction t
        WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.deletedAt IS NULL
          AND t.merchant IS NOT NULL AND TRIM(t.merchant) != ''
          AND t.occurredOn >= :from AND t.occurredOn <= :to
        GROUP BY t.merchant
        ORDER BY SUM(t.baseAmount) DESC
    """)
    List<Object[]> topMerchants(String userId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("""
        SELECT t.account.id,
               COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.baseAmount ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.baseAmount ELSE 0 END), 0),
               COUNT(t)
        FROM Transaction t
        WHERE t.user.id = :userId AND t.deletedAt IS NULL
          AND t.occurredOn >= :from AND t.occurredOn <= :to
        GROUP BY t.account.id
    """)
    List<Object[]> sumByAccount(String userId, LocalDate from, LocalDate to);
}
