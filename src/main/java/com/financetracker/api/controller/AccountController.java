package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                accountService.list(SecurityUtils.currentUserId(), includeArchived)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> get(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                accountService.get(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(
                accountService.create(SecurityUtils.currentUserId(), body)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> update(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                accountService.update(SecurityUtils.currentUserId(), id, body)));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> archive(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                accountService.archive(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping("/{id}/unarchive")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> unarchive(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                accountService.unarchive(SecurityUtils.currentUserId(), id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> remove(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                accountService.remove(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping("/reorder")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> reorder(@RequestBody Map<String, Object> body) {
        List<String> orderedIds = (List<String>) body.get("orderedIds");
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                accountService.reorder(SecurityUtils.currentUserId(), orderedIds)));
    }
}
