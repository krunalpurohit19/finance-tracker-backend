package com.financetracker.api.repository;

import com.financetracker.api.entity.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, String> {

    List<GoalContribution> findByGoalIdAndDeletedAtIsNullOrderByOccurredOnDesc(String goalId);

    Optional<GoalContribution> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM GoalContribution c WHERE c.goal.id = :goalId AND c.deletedAt IS NULL")
    BigDecimal sumByGoalId(String goalId);
}
