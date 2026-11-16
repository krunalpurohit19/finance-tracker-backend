package com.financetracker.api.repository;

import com.financetracker.api.entity.SavingsGoal;
import com.financetracker.api.entity.enums.GoalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, String> {

    List<SavingsGoal> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String userId);

    List<SavingsGoal> findByUserIdAndStatusAndDeletedAtIsNull(String userId, GoalStatus status);

    Optional<SavingsGoal> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    /**
     * The goal, row-locked (SELECT ... FOR UPDATE) until the transaction ends: serialises changes to
     * one goal's contributions so "saved never goes below zero" can't be raced. Explicit JPQL, not a
     * derived name, which would join and lock the user row too. Must be the transaction's first read.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM SavingsGoal g WHERE g.id = :id AND g.user.id = :userId AND g.deletedAt IS NULL")
    Optional<SavingsGoal> lockOwned(String id, String userId);
}
