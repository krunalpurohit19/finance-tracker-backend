package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.IdResponse;
import com.financetracker.api.dto.budget.BudgetDtos.*;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.BudgetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    /** @param month "YYYY-MM"; defaults to the current month */
    @GetMapping
    public ApiEnvelope.Success<List<BudgetProgress>> list(@RequestParam(required = false) YearMonth month) {
        return new ApiEnvelope.Success<>(budgetService.list(SecurityUtils.currentUserId(), month));
    }

    @GetMapping("/history")
    public ApiEnvelope.Success<List<MonthSpend>> history(
            @RequestParam(defaultValue = "6") @Min(value = 1, message = "Between 1 and 24 months")
            @Max(value = 24, message = "Between 1 and 24 months") int months,
            @RequestParam(required = false) String categoryId) {
        return new ApiEnvelope.Success<>(budgetService.history(SecurityUtils.currentUserId(), months, categoryId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope.Success<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest body) {
        return new ApiEnvelope.Success<>(budgetService.create(SecurityUtils.currentUserId(), body));
    }

    @PatchMapping("/{id}")
    public ApiEnvelope.Success<BudgetResponse> update(@PathVariable String id, @Valid @RequestBody UpdateBudgetRequest body) {
        return new ApiEnvelope.Success<>(budgetService.update(SecurityUtils.currentUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope.Success<IdResponse> remove(@PathVariable String id) {
        budgetService.remove(SecurityUtils.currentUserId(), id);
        return new ApiEnvelope.Success<>(new IdResponse(id));
    }
}
