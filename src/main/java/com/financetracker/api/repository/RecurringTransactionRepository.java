package com.financetracker.api.repository;

import com.financetracker.api.entity.RecurringTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, String> {

    /** With account and category fetched: the list shows their names. */
    @Query("""
        SELECT r FROM RecurringTransaction r JOIN FETCH r.account LEFT JOIN FETCH r.category
        WHERE r.user.id = :userId AND r.deletedAt IS NULL
        ORDER BY r.nextOccurrence
    """)
    List<RecurringTransaction> findByUserIdAndDeletedAtIsNullOrderByNextOccurrence(String userId);

    Optional<RecurringTransaction> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    /**
     * Rules due by {@code until}, row-locked (SELECT ... FOR UPDATE) until the transaction ends, so two
     * generate calls for one user run one after the other instead of racing to insert the same
     * occurrence: a locking read returns the latest committed nextOccurrence, which the other run has
     * moved past everything it posted. No fetch join: that would lock the account and category rows too.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM RecurringTransaction r
        WHERE r.user.id = :userId AND r.isActive = true AND r.deletedAt IS NULL
          AND r.nextOccurrence <= :until
        ORDER BY r.nextOccurrence
    """)
    List<RecurringTransaction> findDue(String userId, LocalDate until);

    @Query("""
        SELECT r FROM RecurringTransaction r JOIN FETCH r.account LEFT JOIN FETCH r.category
        WHERE r.user.id = :userId AND r.isActive = true AND r.deletedAt IS NULL
        ORDER BY r.nextOccurrence
    """)
    List<RecurringTransaction> findUpcoming(String userId);
}
