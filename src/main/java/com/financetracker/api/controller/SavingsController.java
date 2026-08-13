package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.*;
import com.financetracker.api.entity.enums.GoalStatus;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.*;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/v1/savings")
public class SavingsController {

    private final SavingsGoalRepository goalRepo;
    private final GoalContributionRepository contribRepo;
    private final TransactionRepository txRepo;
    private final UserSettingsRepository settingsRepo;

    public SavingsController(SavingsGoalRepository goalRepo, GoalContributionRepository contribRepo,
                              TransactionRepository txRepo, UserSettingsRepository settingsRepo) {
        this.goalRepo = goalRepo;
        this.contribRepo = contribRepo;
        this.txRepo = txRepo;
        this.settingsRepo = settingsRepo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> savings() {
        String userId = SecurityUtils.currentUserId();
        var settings = settingsRepo.findById(userId).orElseThrow();
        String baseCurrency = settings.getBaseCurrency();

        List<Object[]> lifetimeList = txRepo.sumIncomeAndExpense(userId, LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31));
        Object[] lifetimeSums = lifetimeList.isEmpty() ? new Object[]{0, 0} : lifetimeList.get(0);
        BigDecimal lifetime = new BigDecimal(lifetimeSums[0].toString()).subtract(new BigDecimal(lifetimeSums[1].toString()));

        YearMonth ym = YearMonth.now();
        List<Object[]> thisMonthList = txRepo.sumIncomeAndExpense(userId, ym.atDay(1), ym.atEndOfMonth());
        Object[] thisMonthSums = thisMonthList.isEmpty() ? new Object[]{0, 0} : thisMonthList.get(0);
        BigDecimal tmIncome = new BigDecimal(thisMonthSums[0].toString());
        BigDecimal tmExpense = new BigDecimal(thisMonthSums[1].toString());
        BigDecimal tmSaved = tmIncome.subtract(tmExpense);
        
        Double thisMonthRate = null;
        if (tmIncome.compareTo(BigDecimal.ZERO) > 0) {
            thisMonthRate = tmSaved.divide(tmIncome, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }

        LocalDate yearAgo = ym.minusMonths(11).atDay(1);
        List<Object[]> rawTrend = txRepo.monthlyTrend(userId, yearAgo, ym.atEndOfMonth());
        
        List<Map<String, Object>> trend = new ArrayList<>();
        Map<String, Object> best = null;
        Map<String, Object> worst = null;
        BigDecimal sumSaved = BigDecimal.ZERO;
        
        BigDecimal bestSaved = new BigDecimal("-99999999");
        BigDecimal worstSaved = new BigDecimal("99999999");

        for (Object[] r : rawTrend) {
            String month = (String) r[0];
            BigDecimal inc = new BigDecimal(r[1].toString());
            BigDecimal exp = new BigDecimal(r[2].toString());
            BigDecimal net = inc.subtract(exp);
            
            Double rate = null;
            if (inc.compareTo(BigDecimal.ZERO) > 0) {
                rate = net.divide(inc, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
            }
            
            trend.add(Map.of(
                "month", month,
                "income", inc.toPlainString(),
                "expenses", exp.toPlainString(),
                "saved", net.toPlainString(),
                "rate", rate != null ? rate : -1 // -1 just as placeholder, will override below
            ));
            
            sumSaved = sumSaved.add(net);
            
            if (net.compareTo(bestSaved) > 0) {
                bestSaved = net;
                best = Map.of("month", month, "saved", net.toPlainString());
            }
            if (net.compareTo(worstSaved) < 0) {
                worstSaved = net;
                worst = Map.of("month", month, "saved", net.toPlainString());
            }
        }
        
        // fix rate mapping (since map.of doesn't allow nulls easily without casting)
        for (int i = 0; i < trend.size(); i++) {
            Map<String, Object> t = new HashMap<>(trend.get(i));
            if (t.get("rate").equals(-1)) t.put("rate", null);
            trend.set(i, t);
        }

        BigDecimal averageMonthly = null;
        if (!trend.isEmpty()) {
            averageMonthly = sumSaved.divide(new BigDecimal(trend.size()), 2, RoundingMode.HALF_UP);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseCurrency", baseCurrency);
        data.put("lifetime", lifetime.toPlainString());
        data.put("thisMonth", tmSaved.toPlainString());
        data.put("thisMonthRate", thisMonthRate);
        data.put("averageMonthly", averageMonthly != null ? averageMonthly.toPlainString() : null);
        data.put("best", best);
        data.put("worst", worst);
        data.put("trend", trend);

        return ResponseEntity.ok(new ApiEnvelope.Success<>(data));
    }

    @GetMapping("/goals")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> listGoals() {
        String userId = SecurityUtils.currentUserId();
        List<SavingsGoal> goals = goalRepo.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(goals.stream().map(this::goalToMap).toList()));
    }

    @PostMapping("/goals")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> createGoal(@RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.currentUserId();
        SavingsGoal goal = SavingsGoal.builder()
                .id(UUID.randomUUID().toString())
                .user(User.builder().id(userId).build())
                .name((String) body.get("name"))
                .targetAmount(new BigDecimal((String) body.get("targetAmount")))
                .targetDate(body.containsKey("targetDate") && body.get("targetDate") != null ? LocalDate.parse((String) body.get("targetDate")) : null)
                .color((String) body.get("color"))
                .icon((String) body.get("icon"))
                .status(GoalStatus.ACTIVE)
                .build();
        goalRepo.save(goal);
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(goalToMap(goal)));
    }

    @GetMapping("/goals/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> getGoal(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(goalToMap(findGoal(id))));
    }

    @PatchMapping("/goals/{id}")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> updateGoal(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        SavingsGoal goal = findGoal(id);
        if (body.containsKey("name")) goal.setName((String) body.get("name"));
        if (body.containsKey("targetAmount")) goal.setTargetAmount(new BigDecimal((String) body.get("targetAmount")));
        if (body.containsKey("targetDate")) goal.setTargetDate(body.get("targetDate") != null ? LocalDate.parse((String) body.get("targetDate")) : null);
        if (body.containsKey("color")) goal.setColor((String) body.get("color"));
        if (body.containsKey("icon")) goal.setIcon((String) body.get("icon"));
        if (body.containsKey("status")) goal.setStatus(GoalStatus.valueOf((String) body.get("status")));
        goalRepo.save(goal);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(goalToMap(goal)));
    }

    @PostMapping("/goals/{id}/archive")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> archiveGoal(@PathVariable String id) {
        SavingsGoal goal = findGoal(id);
        goal.setStatus(GoalStatus.ARCHIVED);
        goal.setArchivedAt(Instant.now());
        goalRepo.save(goal);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(goalToMap(goal)));
    }

    @DeleteMapping("/goals/{id}")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> removeGoal(@PathVariable String id) {
        SavingsGoal goal = findGoal(id);
        goal.setDeletedAt(Instant.now());
        goalRepo.save(goal);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("id", id)));
    }

    @GetMapping("/goals/{id}/contributions")
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> contributions(@PathVariable String id) {
        findGoal(id); // ownership check
        List<GoalContribution> contribs = contribRepo.findByGoalIdAndDeletedAtIsNullOrderByOccurredOnDesc(id);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(contribs.stream().map(this::contribToMap).toList()));
    }

    @PostMapping("/goals/{id}/contributions")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> contribute(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        SavingsGoal goal = findGoal(id);
        if (goal.getStatus() == GoalStatus.ARCHIVED) throw ApiException.goalArchived("This goal is archived");

        GoalContribution c = GoalContribution.builder()
                .id(UUID.randomUUID().toString())
                .user(User.builder().id(SecurityUtils.currentUserId()).build())
                .goal(goal)
                .amount(new BigDecimal((String) body.get("amount")))
                .occurredOn(LocalDate.parse((String) body.get("occurredOn")))
                .notes((String) body.get("notes"))
                .build();
        contribRepo.save(c);
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(contribToMap(c)));
    }

    @DeleteMapping("/contributions/{id}")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> removeContribution(@PathVariable String id) {
        String userId = SecurityUtils.currentUserId();
        GoalContribution c = contribRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Contribution not found"));
        c.setDeletedAt(Instant.now());
        contribRepo.save(c);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("id", id)));
    }

    private SavingsGoal findGoal(String id) {
        return goalRepo.findByIdAndUserIdAndDeletedAtIsNull(id, SecurityUtils.currentUserId())
                .orElseThrow(() -> ApiException.notFound("Goal not found"));
    }

    private Map<String, Object> goalToMap(SavingsGoal g) {
        BigDecimal saved = contribRepo.sumByGoalId(g.getId());
        BigDecimal remaining = g.getTargetAmount().subtract(saved);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        Double progress = null;
        if (g.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            progress = saved.divide(g.getTargetAmount(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }

        boolean achieved = saved.compareTo(g.getTargetAmount()) >= 0;

        Long monthsRemaining = null;
        BigDecimal requiredMonthly = null;
        boolean overdue = false;

        if (g.getTargetDate() != null) {
            if (g.getTargetDate().isBefore(LocalDate.now()) && !achieved) {
                overdue = true;
            } else if (!achieved) {
                long months = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(g.getTargetDate()));
                if (months < 1) months = 1;
                monthsRemaining = months;
                requiredMonthly = remaining.divide(new BigDecimal(months), 2, RoundingMode.HALF_UP);
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("name", g.getName());
        m.put("targetAmount", g.getTargetAmount().toPlainString());
        m.put("saved", saved.toPlainString());
        m.put("remaining", remaining.toPlainString());
        m.put("progress", progress);
        m.put("targetDate", g.getTargetDate() != null ? g.getTargetDate().toString() : null);
        m.put("monthsRemaining", monthsRemaining);
        m.put("requiredMonthly", requiredMonthly != null ? requiredMonthly.toPlainString() : null);
        m.put("overdue", overdue);
        m.put("achieved", achieved);
        m.put("color", g.getColor());
        m.put("icon", g.getIcon());
        m.put("status", g.getStatus() != null ? g.getStatus().name() : "ACTIVE");
        m.put("archived", g.getStatus() == GoalStatus.ARCHIVED);
        return m;
    }

    private Map<String, Object> contribToMap(GoalContribution c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("amount", c.getAmount().toPlainString());
        m.put("occurredOn", c.getOccurredOn().toString());
        m.put("transactionId", c.getTransaction() != null ? c.getTransaction().getId() : null);
        m.put("notes", c.getNotes());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }
}
