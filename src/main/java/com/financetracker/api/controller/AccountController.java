package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.IdResponse;
import com.financetracker.api.dto.account.AccountDtos.*;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiEnvelope.Success<List<AccountResponse>> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return new ApiEnvelope.Success<>(accountService.list(SecurityUtils.currentUserId(), includeArchived));
    }

    @GetMapping("/{id}")
    public ApiEnvelope.Success<AccountResponse> get(@PathVariable String id) {
        return new ApiEnvelope.Success<>(accountService.get(SecurityUtils.currentUserId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope.Success<AccountResponse> create(@Valid @RequestBody CreateAccountRequest body) {
        return new ApiEnvelope.Success<>(accountService.create(SecurityUtils.currentUserId(), body));
    }

    @PatchMapping("/{id}")
    public ApiEnvelope.Success<AccountResponse> update(@PathVariable String id, @Valid @RequestBody UpdateAccountRequest body) {
        return new ApiEnvelope.Success<>(accountService.update(SecurityUtils.currentUserId(), id, body));
    }

    @PostMapping("/{id}/archive")
    public ApiEnvelope.Success<AccountResponse> archive(@PathVariable String id) {
        return new ApiEnvelope.Success<>(accountService.archive(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/unarchive")
    public ApiEnvelope.Success<AccountResponse> unarchive(@PathVariable String id) {
        return new ApiEnvelope.Success<>(accountService.unarchive(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope.Success<IdResponse> remove(@PathVariable String id) {
        accountService.remove(SecurityUtils.currentUserId(), id);
        return new ApiEnvelope.Success<>(new IdResponse(id));
    }

    @PostMapping("/reorder")
    public ApiEnvelope.Success<Reordered> reorder(@Valid @RequestBody ReorderRequest body) {
        return new ApiEnvelope.Success<>(accountService.reorder(SecurityUtils.currentUserId(), body.orderedIds()));
    }
}
