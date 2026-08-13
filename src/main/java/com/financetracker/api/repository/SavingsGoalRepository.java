package com.financetracker.api.repository;

import com.financetracker.api.entity.SavingsGoal;
import com.financetracker.api.entity.enums.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, String> {

    List<SavingsGoal> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String userId);

    List<SavingsGoal> findByUserIdAndStatusAndDeletedAtIsNull(String userId, GoalStatus status);

    Optional<SavingsGoal> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);
}
