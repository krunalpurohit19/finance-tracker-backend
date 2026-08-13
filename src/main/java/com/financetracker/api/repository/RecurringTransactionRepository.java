package com.financetracker.api.repository;

import com.financetracker.api.entity.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, String> {

    List<RecurringTransaction> findByUserIdAndDeletedAtIsNullOrderByNextOccurrence(String userId);

    Optional<RecurringTransaction> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("""
        SELECT r FROM RecurringTransaction r
        WHERE r.user.id = :userId AND r.isActive = true AND r.deletedAt IS NULL
          AND r.nextOccurrence <= :until
        ORDER BY r.nextOccurrence
    """)
    List<RecurringTransaction> findDue(String userId, LocalDate until);

    @Query("""
        SELECT r FROM RecurringTransaction r
        WHERE r.user.id = :userId AND r.isActive = true AND r.deletedAt IS NULL
        ORDER BY r.nextOccurrence
    """)
    List<RecurringTransaction> findUpcoming(String userId);
}
