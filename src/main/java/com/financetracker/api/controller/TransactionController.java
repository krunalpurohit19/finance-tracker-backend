package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService txService;

    public TransactionController(TransactionService txService) {
        this.txService = txService;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> list(@RequestParam Map<String, String> query) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                txService.list(SecurityUtils.currentUserId(), query)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> get(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                txService.get(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(
                txService.create(SecurityUtils.currentUserId(), body)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> update(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                txService.update(SecurityUtils.currentUserId(), id, body)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> remove(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                txService.remove(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> duplicate(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        String occurredOn = body != null ? (String) body.get("occurredOn") : null;
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(
                txService.duplicate(SecurityUtils.currentUserId(), id, occurredOn)));
    }
}
