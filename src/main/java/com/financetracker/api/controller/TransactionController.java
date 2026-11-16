package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.IdResponse;
import com.financetracker.api.dto.transaction.TransactionDtos.*;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService txService;

    public TransactionController(TransactionService txService) {
        this.txService = txService;
    }

    @GetMapping
    public ApiEnvelope.Success<TransactionPage> list(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "Between 1 and 100")
            @Max(value = 100, message = "Between 1 and 100") int limit) {
        return new ApiEnvelope.Success<>(txService.list(SecurityUtils.currentUserId(), type, accountId, categoryId,
                from, to, cursor, limit));
    }

    @GetMapping("/{id}")
    public ApiEnvelope.Success<TransactionResponse> get(@PathVariable String id) {
        return new ApiEnvelope.Success<>(txService.get(SecurityUtils.currentUserId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope.Success<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest body) {
        return new ApiEnvelope.Success<>(txService.create(SecurityUtils.currentUserId(), body));
    }

    @PatchMapping("/{id}")
    public ApiEnvelope.Success<TransactionResponse> update(@PathVariable String id,
                                                           @Valid @RequestBody UpdateTransactionRequest body) {
        return new ApiEnvelope.Success<>(txService.update(SecurityUtils.currentUserId(), id, body));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope.Success<IdResponse> remove(@PathVariable String id) {
        txService.remove(SecurityUtils.currentUserId(), id);
        return new ApiEnvelope.Success<>(new IdResponse(id));
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope.Success<TransactionResponse> duplicate(@PathVariable String id,
                                                              @RequestBody(required = false) DuplicateRequest body) {
        return new ApiEnvelope.Success<>(txService.duplicate(SecurityUtils.currentUserId(), id,
                body != null ? body.occurredOn() : null));
    }
}
