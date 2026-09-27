package com.financetracker.api.repository;

import com.financetracker.api.entity.GoalContribution;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, String> {

    List<GoalContribution> findByGoalIdAndDeletedAtIsNullOrderByOccurredOnDesc(String goalId);

    Optional<GoalContribution> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    /** Row-locked lookup, so a delete's first read doesn't open a snapshot before the goal lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM GoalContribution c WHERE c.id = :id AND c.user.id = :userId AND c.deletedAt IS NULL")
    Optional<GoalContribution> lockOwned(String id, String userId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM GoalContribution c WHERE c.goal.id = :goalId AND c.deletedAt IS NULL")
    BigDecimal sumByGoalId(String goalId);

    /** [goalId, saved] for each of {@code goalIds} that has contributions — one query for a goal list. */
    @Query("""
        SELECT c.goal.id, COALESCE(SUM(c.amount), 0) FROM GoalContribution c
        WHERE c.goal.id IN :goalIds AND c.deletedAt IS NULL
        GROUP BY c.goal.id
    """)
    List<Object[]> sumByGoalIds(List<String> goalIds);
}
