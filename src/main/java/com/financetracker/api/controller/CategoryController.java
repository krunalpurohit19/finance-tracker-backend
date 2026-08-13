package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> list(
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                categoryService.list(SecurityUtils.currentUserId(), kind, includeArchived)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> get(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(categoryService.get(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(new ApiEnvelope.Success<>(
                categoryService.create(SecurityUtils.currentUserId(), body)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> update(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(
                categoryService.update(SecurityUtils.currentUserId(), id, body)));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> archive(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(categoryService.archive(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping("/{id}/unarchive")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> unarchive(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(categoryService.unarchive(SecurityUtils.currentUserId(), id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> remove(@PathVariable String id) {
        return ResponseEntity.ok(new ApiEnvelope.Success<>(categoryService.remove(SecurityUtils.currentUserId(), id)));
    }
}
