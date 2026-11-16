package com.financetracker.api.dto.category;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.financetracker.api.dto.Inputs;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.enums.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request/response shapes for /api/v1/categories. Rules mirror mobile src/validation/categories.ts. */
public final class CategoryDtos {

    private CategoryDtos() {}

    public record CreateCategoryRequest(
            @NotBlank(message = "Required") @Size(max = 60, message = "Keep this under 60 characters") String name,
            @NotNull(message = "Required") CategoryKind kind,
            @Size(min = 1, max = 64, message = "Choose a category") String parentId,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Enter a colour as #RRGGBB") String color,
            @Size(max = 40, message = "Keep this under 40 characters") String icon) {
        public CreateCategoryRequest {
            name = Inputs.strip(name);
            parentId = Inputs.strip(parentId);
            color = Inputs.strip(color);
            icon = Inputs.strip(icon);
        }
    }

    /** PATCH: a field left out (or null) is left unchanged. A category's kind never changes. */
    public record UpdateCategoryRequest(
            @Size(min = 1, message = "Required") @Size(max = 60, message = "Keep this under 60 characters") String name,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Enter a colour as #RRGGBB") String color,
            @Size(max = 40, message = "Keep this under 40 characters") String icon) {
        public UpdateCategoryRequest {
            name = Inputs.strip(name);
            color = Inputs.strip(color);
            icon = Inputs.strip(icon);
        }
    }

    /** {@code archivedAt} is always present (null when active): the app tests it with {@code !== null}. */
    public record CategoryResponse(String id, String name, CategoryKind kind, String color, String icon,
                                   boolean isSystem, int sortOrder, String parentId,
                                   @JsonInclude(JsonInclude.Include.ALWAYS) Instant archivedAt) {
        public static CategoryResponse of(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getKind(), c.getColor(), c.getIcon(), c.isSystem(),
                    c.getSortOrder(), c.getParent() != null ? c.getParent().getId() : null, c.getArchivedAt());
        }
    }
}
