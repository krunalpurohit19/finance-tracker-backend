package com.financetracker.api.service;

import com.financetracker.api.dto.savings.SavingsDtos.*;
import com.financetracker.api.entity.GoalContribution;
import com.financetracker.api.entity.SavingsGoal;
import com.financetracker.api.entity.enums.GoalStatus;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.GoalContributionRepository;
import com.financetracker.api.repository.SavingsGoalRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SavingsService {

    private static final LocalDate EPOCH_START = LocalDate.of(1970, 1, 1);
    private static final LocalDate END_OF_TIME = LocalDate.of(9999, 12, 31);
    /** Largest DECIMAL(18,4), and the most the mobile money() accepts (14 integer digits). */
    private static final BigDecimal MAX_SAVED = new BigDecimal("99999999999999.9999");
    /** DECIMAL(18,4) zero, as MySQL returns an empty SUM. */
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private final SavingsGoalRepository goalRepo;
    private final GoalContributionRepository contribRepo;
    private final TransactionRepository txRepo;
    private final UserSettingsRepository settingsRepo;
    private final UserRepository userRepo;

    public SavingsService(SavingsGoalRepository goalRepo, GoalContributionRepository contribRepo,
                          TransactionRepository txRepo, UserSettingsRepository settingsRepo, UserRepository userRepo) {
        this.goalRepo = goalRepo;
        this.contribRepo = contribRepo;
        this.txRepo = txRepo;
        this.settingsRepo = settingsRepo;
        this.userRepo = userRepo;
    }

    /** Savings = income − expenses (base currency; transfers excluded): lifetime, this month, last 12 months. */
    @Transactional(readOnly = true)
    public SavingsSummary getSummary(String userId) {
        String baseCurrency = settingsRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Settings not found")).getBaseCurrency();

        BigDecimal[] lifetime = incomeAndExpense(userId, EPOCH_START, END_OF_TIME);
        YearMonth ym = YearMonth.now();
        BigDecimal[] month = incomeAndExpense(userId, ym.atDay(1), ym.atEndOfMonth());
        BigDecimal thisMonth = month[0].subtract(month[1]);

        List<TrendPoint> trend = new ArrayList<>();
        MonthSaved best = null;
        MonthSaved worst = null;
        BigDecimal sumSaved = BigDecimal.ZERO;
        for (Object[] r : txRepo.monthlyTrend(userId, ym.minusMonths(11).atDay(1), ym.atEndOfMonth())) {
            String m = (String) r[0];
            BigDecimal income = decimal(r[1]);
            BigDecimal expenses = decimal(r[2]);
            BigDecimal saved = income.subtract(expenses);
            trend.add(new TrendPoint(m, income, expenses, saved, Ratios.percent(saved, income)));
            sumSaved = sumSaved.add(saved);
            if (best == null || saved.compareTo(best.saved()) > 0) best = new MonthSaved(m, saved);
            if (worst == null || saved.compareTo(worst.saved()) < 0) worst = new MonthSaved(m, saved);
        }
        BigDecimal averageMonthly = trend.isEmpty() ? null
                : sumSaved.divide(new BigDecimal(trend.size()), 2, RoundingMode.HALF_UP);

        return new SavingsSummary(baseCurrency, lifetime[0].subtract(lifetime[1]), thisMonth,
                Ratios.percent(thisMonth, month[0]), averageMonthly, best, worst, trend);
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(String userId) {
        List<SavingsGoal> goals = goalRepo.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        if (goals.isEmpty()) return List.of();
        Map<String, BigDecimal> saved = new HashMap<>();
        for (Object[] r : contribRepo.sumByGoalIds(goals.stream().map(SavingsGoal::getId).toList())) {
            saved.put((String) r[0], decimal(r[1]));
        }
        LocalDate today = LocalDate.now();
        return goals.stream().map(g -> progress(g, saved.getOrDefault(g.getId(), ZERO), today)).toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoal(String userId, String id) {
        return toResponse(goal(userId, id));
    }

    @Transactional
    public GoalResponse createGoal(String userId, CreateGoalRequest req) {
        SavingsGoal goal = SavingsGoal.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .name(req.name())
                .targetAmount(req.targetAmount().setScale(4)) // DECIMAL(18,4); @Digits makes this exact
                .targetDate(req.targetDate())
                .color(req.color())
                .icon(req.icon())
                .status(GoalStatus.ACTIVE)
                .build();
        goalRepo.save(goal);
        return progress(goal, ZERO, LocalDate.now()); // nothing contributed yet
    }

    @Transactional
    public GoalResponse updateGoal(String userId, String id, UpdateGoalRequest req) {
        SavingsGoal goal = goal(userId, id);
        if (req.getName() != null) goal.setName(req.getName());
        if (req.getTargetAmount() != null) goal.setTargetAmount(req.getTargetAmount().setScale(4));
        if (req.getTargetDate() != null) goal.setTargetDate(req.getTargetDate().orElse(null));
        if (req.getColor() != null) goal.setColor(req.getColor());
        if (req.getIcon() != null) goal.setIcon(req.getIcon());
        if (req.getStatus() != null) {
            goal.setStatus(req.getStatus());
            // archivedAt follows the status, whichever endpoint set it
            if (req.getStatus() != GoalStatus.ARCHIVED) goal.setArchivedAt(null);
            else if (goal.getArchivedAt() == null) goal.setArchivedAt(Instant.now());
        }
        return toResponse(goal);
    }

    @Transactional
    public GoalResponse archiveGoal(String userId, String id) {
        SavingsGoal goal = goal(userId, id);
        goal.setStatus(GoalStatus.ARCHIVED);
        goal.setArchivedAt(Instant.now());
        return toResponse(goal);
    }

    @Transactional
    public void removeGoal(String userId, String id) {
        goal(userId, id).setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ContributionResponse> listContributions(String userId, String goalId) {
        goal(userId, goalId); // ownership
        return contribRepo.findByGoalIdAndDeletedAtIsNullOrderByOccurredOnDesc(goalId).stream()
                .map(ContributionResponse::of).toList();
    }

    /** Saved stays within 0 … MAX_SAVED: a withdrawal can't exceed it, a deposit can't overflow it. */
    @Transactional
    public ContributionResponse contribute(String userId, String goalId, ContributeRequest req) {
        SavingsGoal goal = goalRepo.lockOwned(goalId, userId) // first read: see the note on lockOwned
                .orElseThrow(() -> ApiException.notFound("Goal not found"));
        if (goal.getStatus() == GoalStatus.ARCHIVED) throw ApiException.goalArchived("This goal is archived");
        if (req.amount().signum() == 0) throw invalidAmount("Enter an amount above or below zero");
        BigDecimal after = contribRepo.sumByGoalId(goalId).add(req.amount());
        if (after.signum() < 0) throw invalidAmount("You can't withdraw more than you've saved toward this goal");
        if (after.compareTo(MAX_SAVED) > 0) throw invalidAmount("That's more than this goal can hold");
        GoalContribution c = GoalContribution.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .goal(goal)
                .amount(req.amount().setScale(4))
                .occurredOn(req.occurredOn() != null ? req.occurredOn() : LocalDate.now())
                .notes(req.notes())
                .build();
        return ContributionResponse.of(contribRepo.save(c)); // the saved copy carries createdAt
    }

    /** Removing a deposit is a withdrawal: refused if later withdrawals depend on it. */
    @Transactional
    public void removeContribution(String userId, String id) {
        GoalContribution c = contribRepo.lockOwned(id, userId)
                .orElseThrow(() -> ApiException.notFound("Contribution not found"));
        String goalId = c.getGoal().getId();
        // A deleted goal has no balance left to protect; otherwise lock it before summing.
        if (c.getAmount().signum() > 0 && goalRepo.lockOwned(goalId, userId).isPresent()
                && contribRepo.sumByGoalId(goalId).subtract(c.getAmount()).signum() < 0) {
            throw ApiException.conflict("Remove the withdrawals from this goal first");
        }
        c.setDeletedAt(Instant.now());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private GoalResponse toResponse(SavingsGoal g) {
        BigDecimal saved = contribRepo.sumByGoalId(g.getId());
        return progress(g, saved, LocalDate.now());
    }

    /** Progress toward the target and, for a deadline, what it takes to get there from {@code today}. */
    static GoalResponse progress(SavingsGoal g, BigDecimal saved, LocalDate today) {
        BigDecimal target = g.getTargetAmount();
        BigDecimal remaining = target.subtract(saved);
        if (remaining.signum() < 0) remaining = BigDecimal.ZERO;
        boolean achieved = saved.compareTo(target) >= 0;

        Long monthsRemaining = null;
        BigDecimal requiredMonthly = null;
        boolean overdue = false;
        if (g.getTargetDate() != null && !achieved) {
            if (g.getTargetDate().isBefore(today)) {
                overdue = true;
                requiredMonthly = remaining; // mobile shows "Needs {requiredMonthly} to finish"
            } else {
                long months = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(g.getTargetDate())));
                monthsRemaining = months;
                requiredMonthly = remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
            }
        }
        GoalStatus status = g.getStatus() != null ? g.getStatus() : GoalStatus.ACTIVE;
        return new GoalResponse(g.getId(), g.getName(), target, saved, remaining, Ratios.percent(saved, target),
                g.getTargetDate(), monthsRemaining, requiredMonthly, overdue, achieved, g.getColor(), g.getIcon(),
                status, status == GoalStatus.ARCHIVED);
    }

    private SavingsGoal goal(String userId, String id) {
        return goalRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Goal not found"));
    }

    /** [income, expense] in base currency; the aggregate query returns one row. */
    private BigDecimal[] incomeAndExpense(String userId, LocalDate from, LocalDate to) {
        List<Object[]> rows = txRepo.sumIncomeAndExpense(userId, from, to);
        if (rows.isEmpty()) return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        return new BigDecimal[]{decimal(rows.get(0)[0]), decimal(rows.get(0)[1])};
    }

    private static BigDecimal decimal(Object o) {
        return new BigDecimal(o.toString());
    }

    private static ApiException invalidAmount(String message) {
        return ApiException.validationFailed(message, Map.of("amount", List.of(message)));
    }
}
