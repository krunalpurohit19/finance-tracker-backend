package com.financetracker.api.repository;

import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.enums.CategoryKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {

    @Query("SELECT c FROM Category c WHERE c.user.id = :userId AND c.deletedAt IS NULL ORDER BY c.sortOrder")
    List<Category> findActiveByUserId(String userId);

    @Query("SELECT c FROM Category c WHERE c.user.id = :userId AND c.kind = :kind AND c.deletedAt IS NULL AND c.archivedAt IS NULL ORDER BY c.sortOrder")
    List<Category> findActiveByUserIdAndKind(String userId, CategoryKind kind);

    Optional<Category> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.category.id = :categoryId AND t.deletedAt IS NULL")
    long countTransactionsByCategoryId(String categoryId);

    @Query("SELECT COUNT(b) FROM Budget b WHERE b.category.id = :categoryId AND b.deletedAt IS NULL")
    long countBudgetsByCategoryId(String categoryId);
}
