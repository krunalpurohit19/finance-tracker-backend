package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.IdResponse;
import com.financetracker.api.dto.recurring.RecurringDtos.*;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.RecurringService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recurring")
public class RecurringController {

    private final RecurringService recurringService;

    public RecurringController(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    public ApiEnvelope.Success<List<RecurringResponse>> list() {
        return new ApiEnvelope.Success<>(recurringService.list(SecurityUtils.currentUserId()));
    }

    @GetMapping("/upcoming")
    public ApiEnvelope.Success<List<RecurringResponse>> upcoming() {
        return new ApiEnvelope.Success<>(recurringService.listUpcoming(SecurityUtils.currentUserId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope.Success<RecurringResponse> create(@Valid @RequestBody CreateRecurringRequest body) {
        return new ApiEnvelope.Success<>(recurringService.create(SecurityUtils.currentUserId(), body));
    }

    @PatchMapping("/{id}")
    public ApiEnvelope.Success<RecurringResponse> update(@PathVariable String id, @Valid @RequestBody UpdateRecurringRequest body) {
        return new ApiEnvelope.Success<>(recurringService.update(SecurityUtils.currentUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope.Success<IdResponse> remove(@PathVariable String id) {
        recurringService.remove(SecurityUtils.currentUserId(), id);
        return new ApiEnvelope.Success<>(new IdResponse(id));
    }

    /** Called by the mobile app on every launch. */
    @PostMapping("/generate")
    public ApiEnvelope.Success<GenerationResult> generate() {
        return new ApiEnvelope.Success<>(recurringService.generate(SecurityUtils.currentUserId()));
    }
}
