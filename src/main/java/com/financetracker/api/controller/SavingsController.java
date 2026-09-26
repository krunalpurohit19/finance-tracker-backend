package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.IdResponse;
import com.financetracker.api.dto.savings.SavingsDtos.*;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.SavingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/savings")
public class SavingsController {

    private final SavingsService savingsService;

    public SavingsController(SavingsService savingsService) {
        this.savingsService = savingsService;
    }

    @GetMapping
    public ApiEnvelope.Success<SavingsSummary> summary() {
        return new ApiEnvelope.Success<>(savingsService.getSummary(SecurityUtils.currentUserId()));
    }

    @GetMapping("/goals")
    public ApiEnvelope.Success<List<GoalResponse>> listGoals() {
        return new ApiEnvelope.Success<>(savingsService.listGoals(SecurityUtils.currentUserId()));
    }

    @PostMapping("/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope.Success<GoalResponse> createGoal(@Valid @RequestBody CreateGoalRequest body) {
        return new ApiEnvelope.Success<>(savingsService.createGoal(SecurityUtils.currentUserId(), body));
    }

    @GetMapping("/goals/{id}")
    public ApiEnvelope.Success<GoalResponse> getGoal(@PathVariable String id) {
        return new ApiEnvelope.Success<>(savingsService.getGoal(SecurityUtils.currentUserId(), id));
    }

    @PatchMapping("/goals/{id}")
    public ApiEnvelope.Success<GoalResponse> updateGoal(@PathVariable String id, @Valid @RequestBody UpdateGoalRequest body) {
        return new ApiEnvelope.Success<>(savingsService.updateGoal(SecurityUtils.currentUserId(), id, body));
    }

    @PostMapping("/goals/{id}/archive")
    public ApiEnvelope.Success<GoalResponse> archiveGoal(@PathVariable String id) {
        return new ApiEnvelope.Success<>(savingsService.archiveGoal(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/goals/{id}")
    public ApiEnvelope.Success<IdResponse> removeGoal(@PathVariable String id) {
        savingsService.removeGoal(SecurityUtils.currentUserId(), id);
        return new ApiEnvelope.Success<>(new IdResponse(id));
    }

    @GetMapping("/goals/{id}/contributions")
    public ApiEnvelope.Success<List<ContributionResponse>> contributions(@PathVariable String id) {
        return new ApiEnvelope.Success<>(savingsService.listContributions(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/goals/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope.Success<ContributionResponse> contribute(@PathVariable String id, @Valid @RequestBody ContributeRequest body) {
        return new ApiEnvelope.Success<>(savingsService.contribute(SecurityUtils.currentUserId(), id, body));
    }

    @DeleteMapping("/contributions/{id}")
    public ApiEnvelope.Success<IdResponse> removeContribution(@PathVariable String id) {
        savingsService.removeContribution(SecurityUtils.currentUserId(), id);
        return new ApiEnvelope.Success<>(new IdResponse(id));
    }
}
