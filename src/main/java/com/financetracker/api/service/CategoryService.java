package com.financetracker.api.service;

import com.financetracker.api.dto.category.CategoryDtos.*;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepo;
    private final UserRepository userRepo;

    public CategoryService(CategoryRepository categoryRepo, UserRepository userRepo) {
        this.categoryRepo = categoryRepo;
        this.userRepo = userRepo;
    }

    /** Archived categories are left out unless asked for; {@code kind} null = both kinds. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> list(String userId, CategoryKind kind, boolean includeArchived) {
        return categoryRepo.findActiveByUserId(userId).stream()
                .filter(c -> kind == null || c.getKind() == kind)
                .filter(c -> includeArchived || c.getArchivedAt() == null)
                .map(CategoryResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(String userId, String id) {
        return CategoryResponse.of(category(userId, id));
    }

    @Transactional
    public CategoryResponse create(String userId, CreateCategoryRequest req) {
        Category cat = Category.builder()
                .id(UUID.randomUUID().toString())
                .user(userRepo.getReferenceById(userId))
                .name(req.name())
                .kind(req.kind())
                .color(req.color())
                .icon(req.icon())
                .build();
        if (req.parentId() != null) {
            Category parent = categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(req.parentId(), userId)
                    .orElseThrow(() -> invalid("parentId", "We couldn't find that parent category"));
            if (parent.getKind() != req.kind()) throw invalid("parentId", "The parent must be the same kind of category");
            if (parent.getParent() != null) throw invalid("parentId", "Categories can only be nested one level");
            cat.setParent(parent);
        }
        categoryRepo.save(cat);
        return CategoryResponse.of(cat);
    }

    @Transactional
    public CategoryResponse update(String userId, String id, UpdateCategoryRequest req) {
        Category cat = category(userId, id);
        if (req.name() != null) cat.setName(req.name());
        if (req.color() != null) cat.setColor(req.color());
        if (req.icon() != null) cat.setIcon(req.icon());
        return CategoryResponse.of(cat);
    }

    /** Archiving hides a category from pickers; past transactions keep it. Idempotent. */
    @Transactional
    public CategoryResponse archive(String userId, String id) {
        Category cat = category(userId, id);
        if (cat.getArchivedAt() == null) cat.setArchivedAt(Instant.now());
        return CategoryResponse.of(cat);
    }

    @Transactional
    public CategoryResponse unarchive(String userId, String id) {
        Category cat = category(userId, id);
        cat.setArchivedAt(null);
        return CategoryResponse.of(cat);
    }

    /** Only an unused category can be deleted; otherwise archive it. */
    @Transactional
    public void remove(String userId, String id) {
        Category cat = category(userId, id);
        if (categoryRepo.countTransactionsByCategoryId(id) > 0 || categoryRepo.countBudgetsByCategoryId(id) > 0) {
            throw ApiException.categoryInUse("This category is still used by transactions or budgets");
        }
        cat.setDeletedAt(Instant.now());
    }

    private Category category(String userId, String id) {
        return categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("We couldn't find that category"));
    }

    private static ApiException invalid(String field, String message) {
        return ApiException.validationFailed(message, Map.of(field, List.of(message)));
    }
}
