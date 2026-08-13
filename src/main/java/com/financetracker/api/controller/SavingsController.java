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
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/savings")
public class SavingsController {

    private final SavingsGoalRepository goalRepo;
    private final GoalContributionRepository contribRepo;
    private final TransactionRepository txRepo;

    public SavingsController(SavingsGoalRepository goalRepo, GoalContributionRepository contribRepo,
                              TransactionRepository txRepo) {
        this.goalRepo = goalRepo;
        this.contribRepo = contribRepo;
        this.txRepo = txRepo;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> savings() {
        String userId = SecurityUtils.currentUserId();
        List<SavingsGoal> goals = goalRepo.findByUserIdAndStatusAndDeletedAtIsNull(userId, GoalStatus.ACTIVE);
        BigDecimal totalTarget = BigDecimal.ZERO;
        BigDecimal totalSaved = BigDecimal.ZERO;
        for (SavingsGoal g : goals) {
            totalTarget = totalTarget.add(g.getTargetAmount());
            totalSaved = totalSaved.add(contribRepo.sumByGoalId(g.getId()));
        }
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of(
                "totalTarget", totalTarget.toPlainString(),
                "totalSaved", totalSaved.toPlainString(),
                "activeGoals", goals.size()
        )));
    }

    @GetMapping("/goals")
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> listGoals() {
        String userId = SecurityUtils.currentUserId();
        List<SavingsGoal> goals = goalRepo.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(goals.stream().map(g -> goalToMap(g)).toList()));
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
                .targetDate(body.containsKey("targetDate") ? LocalDate.parse((String) body.get("targetDate")) : null)
                .color((String) body.get("color"))
                .icon((String) body.get("icon"))
                .build();
        goalRepo.save(goal);
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(goalToMap(goal)));
    }

    @GetMapping("/goals/{id}")
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
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("name", g.getName());
        m.put("targetAmount", g.getTargetAmount().toPlainString());
        m.put("savedAmount", saved.toPlainString());
        m.put("targetDate", g.getTargetDate() != null ? g.getTargetDate().toString() : null);
        m.put("color", g.getColor());
        m.put("icon", g.getIcon());
        m.put("status", g.getStatus().name());
        m.put("accountId", g.getAccount() != null ? g.getAccount().getId() : null);
        m.put("createdAt", g.getCreatedAt());
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
