package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.IdResponse;
import com.financetracker.api.dto.category.CategoryDtos.*;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiEnvelope.Success<List<CategoryResponse>> list(
            @RequestParam(required = false) CategoryKind kind,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return new ApiEnvelope.Success<>(categoryService.list(SecurityUtils.currentUserId(), kind, includeArchived));
    }

    @GetMapping("/{id}")
    public ApiEnvelope.Success<CategoryResponse> get(@PathVariable String id) {
        return new ApiEnvelope.Success<>(categoryService.get(SecurityUtils.currentUserId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiEnvelope.Success<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest body) {
        return new ApiEnvelope.Success<>(categoryService.create(SecurityUtils.currentUserId(), body));
    }

    @PatchMapping("/{id}")
    public ApiEnvelope.Success<CategoryResponse> update(@PathVariable String id, @Valid @RequestBody UpdateCategoryRequest body) {
        return new ApiEnvelope.Success<>(categoryService.update(SecurityUtils.currentUserId(), id, body));
    }

    @PostMapping("/{id}/archive")
    public ApiEnvelope.Success<CategoryResponse> archive(@PathVariable String id) {
        return new ApiEnvelope.Success<>(categoryService.archive(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/unarchive")
    public ApiEnvelope.Success<CategoryResponse> unarchive(@PathVariable String id) {
        return new ApiEnvelope.Success<>(categoryService.unarchive(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope.Success<IdResponse> remove(@PathVariable String id) {
        categoryService.remove(SecurityUtils.currentUserId(), id);
        return new ApiEnvelope.Success<>(new IdResponse(id));
    }
}
