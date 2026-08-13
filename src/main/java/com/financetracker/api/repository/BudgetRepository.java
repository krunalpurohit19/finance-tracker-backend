package com.financetracker.api.repository;

import com.financetracker.api.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, String> {

    Optional<Budget> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    /** All budgets effective for a given month (effectiveFrom <= monthEnd AND (effectiveTo IS NULL OR effectiveTo >= monthStart)). */
    @Query("""
        SELECT b FROM Budget b
        WHERE b.user.id = :userId AND b.deletedAt IS NULL
          AND b.effectiveFrom <= :monthEnd
          AND (b.effectiveTo IS NULL OR b.effectiveTo >= :monthStart)
        ORDER BY b.category.id NULLS FIRST
    """)
    List<Budget> findEffectiveForMonth(String userId, LocalDate monthStart, LocalDate monthEnd);

    /** Check for overlapping budgets for the same category. */
    @Query("""
        SELECT COUNT(b) FROM Budget b
        WHERE b.user.id = :userId AND b.deletedAt IS NULL
          AND ((:categoryId IS NULL AND b.category IS NULL) OR b.category.id = :categoryId)
          AND b.id != :excludeId
          AND b.effectiveFrom <= :to
          AND (b.effectiveTo IS NULL OR b.effectiveTo >= :from)
    """)
    long countOverlapping(String userId, String categoryId, String excludeId, LocalDate from, LocalDate to);
}
