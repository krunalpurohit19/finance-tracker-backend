package com.financetracker.api.service;

import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.User;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepo;

    public CategoryService(CategoryRepository categoryRepo) {
        this.categoryRepo = categoryRepo;
    }

    public List<Map<String, Object>> list(String userId, String kindFilter, boolean includeArchived) {
        List<Category> categories;
        if (kindFilter != null) {
            categories = categoryRepo.findActiveByUserIdAndKind(userId, CategoryKind.valueOf(kindFilter));
        } else if (includeArchived) {
            categories = categoryRepo.findActiveByUserId(userId);
        } else {
            categories = categoryRepo.findActiveByUserId(userId).stream()
                    .filter(c -> c.getArchivedAt() == null).toList();
        }
        return categories.stream().map(this::toMap).toList();
    }

    public Map<String, Object> get(String userId, String id) {
        return toMap(findOrThrow(userId, id));
    }

    @Transactional
    public Map<String, Object> create(String userId, Map<String, Object> input) {
        Category cat = Category.builder()
                .id(UUID.randomUUID().toString())
                .user(User.builder().id(userId).build())
                .name((String) input.get("name"))
                .kind(CategoryKind.valueOf((String) input.get("kind")))
                .color((String) input.get("color"))
                .icon((String) input.get("icon"))
                .build();
        if (input.containsKey("parentId")) {
            cat.setParent(findOrThrow(userId, (String) input.get("parentId")));
        }
        categoryRepo.save(cat);
        return toMap(cat);
    }

    @Transactional
    public Map<String, Object> update(String userId, String id, Map<String, Object> input) {
        Category cat = findOrThrow(userId, id);
        if (input.containsKey("name")) cat.setName((String) input.get("name"));
        if (input.containsKey("color")) cat.setColor((String) input.get("color"));
        if (input.containsKey("icon")) cat.setIcon((String) input.get("icon"));
        categoryRepo.save(cat);
        return toMap(cat);
    }

    @Transactional
    public Map<String, Object> archive(String userId, String id) {
        Category cat = findOrThrow(userId, id);
        cat.setArchivedAt(Instant.now());
        categoryRepo.save(cat);
        return toMap(cat);
    }

    @Transactional
    public Map<String, Object> unarchive(String userId, String id) {
        Category cat = findOrThrow(userId, id);
        cat.setArchivedAt(null);
        categoryRepo.save(cat);
        return toMap(cat);
    }

    @Transactional
    public Map<String, Object> remove(String userId, String id) {
        Category cat = findOrThrow(userId, id);
        if (categoryRepo.countTransactionsByCategoryId(id) > 0 || categoryRepo.countBudgetsByCategoryId(id) > 0) {
            throw ApiException.categoryInUse("This category is still used by transactions or budgets");
        }
        cat.setDeletedAt(Instant.now());
        categoryRepo.save(cat);
        return Map.of("id", id);
    }

    private Category findOrThrow(String userId, String id) {
        return categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("We couldn't find that category"));
    }

    private Map<String, Object> toMap(Category c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("kind", c.getKind().name());
        m.put("color", c.getColor());
        m.put("icon", c.getIcon());
        m.put("isSystem", c.isSystem());
        m.put("sortOrder", c.getSortOrder());
        m.put("parentId", c.getParent() != null ? c.getParent().getId() : null);
        m.put("archivedAt", c.getArchivedAt());
        return m;
    }
}
