package com.financetracker.api.repository;

import com.financetracker.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByIdAndDeletedAtIsNull(String id);
    boolean existsByEmail(String email);

    // ── What a user owns (Settings → data footprint). Live rows only. ──

    @Query("SELECT COUNT(a) FROM FinancialAccount a WHERE a.user.id = :userId AND a.deletedAt IS NULL")
    long countAccounts(String userId);

    @Query("SELECT COUNT(c) FROM Category c WHERE c.user.id = :userId AND c.deletedAt IS NULL")
    long countCategories(String userId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId AND t.deletedAt IS NULL")
    long countTransactions(String userId);

    @Query("SELECT COUNT(b) FROM Budget b WHERE b.user.id = :userId AND b.deletedAt IS NULL")
    long countBudgets(String userId);

    @Query("SELECT COUNT(g) FROM SavingsGoal g WHERE g.user.id = :userId AND g.deletedAt IS NULL")
    long countGoals(String userId);

    @Query("SELECT COUNT(r) FROM RecurringTransaction r WHERE r.user.id = :userId AND r.deletedAt IS NULL")
    long countRecurring(String userId);

    @Query("SELECT COUNT(e) FROM ExchangeRate e WHERE e.user.id = :userId AND e.deletedAt IS NULL")
    long countExchangeRates(String userId);
}
