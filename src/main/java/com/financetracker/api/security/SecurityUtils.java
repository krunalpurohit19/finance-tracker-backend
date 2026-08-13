package com.financetracker.api.security;

import com.financetracker.api.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Static accessor for the currently authenticated user.
 * Replaces c.get("user") from the Hono API.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the authenticated User entity from the security context.
     * Only call from within authenticated endpoints — will throw if no user is set.
     */
    public static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return (User) auth.getPrincipal();
    }

    public static String currentUserId() {
        return currentUser().getId();
    }
}
