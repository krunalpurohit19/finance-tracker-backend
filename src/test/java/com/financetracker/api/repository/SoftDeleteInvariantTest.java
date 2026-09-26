package com.financetracker.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariant 5 — every read filters soft-deleted rows — checked over every repository method we
 * declare, without a database: a JPQL {@code @Query} must say "deletedAt IS NULL", a derived query
 * must be named ...DeletedAtIsNull. Deliberate exceptions are listed with their reason.
 */
class SoftDeleteInvariantTest {

    /** "Repository.method" (or "Repository.*") → why it may see soft-deleted rows. */
    static final Map<String, String> EXEMPT = Map.of(
            "TransactionRepository.findByRecurringIdAndOccurrenceOn",
                    "idempotency: a deleted occurrence must not be generated again (uk_tx_recurring_occurrence)",
            "ExchangeRateRepository.findByUserIdAndFromCurrencyAndToCurrencyAndEffectiveFrom",
                    "upsert revives a soft-deleted rate (uk_exchange_rates covers deleted rows)",
            "UserRepository.findByEmail", "sign-in rejects deleted users itself; emails stay unique across them",
            "UserRepository.existsByEmail", "emails stay unique across deleted users (uk_users_email)",
            "RefreshTokenRepository.*", "refresh_tokens has no deleted_at (session credentials)",
            "UserSettingsRepository.*", "user_settings has no deleted_at (1:1 with users)");

    @Test
    void everyDeclaredReadFiltersSoftDeletedRows() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> repo : repositories()) {
            if (EXEMPT.containsKey(repo.getSimpleName() + ".*")) continue;
            for (Method m : repo.getDeclaredMethods()) {
                if (m.isDefault() || m.isSynthetic()) continue;
                String key = repo.getSimpleName() + "." + m.getName();
                if (EXEMPT.containsKey(key)) continue;
                Query q = m.getAnnotation(Query.class);
                boolean filtered = q != null
                        ? q.value().contains("deletedAt IS NULL")
                        : m.getName().contains("DeletedAtIsNull");
                if (!filtered) violations.add(key);
            }
        }
        assertThat(violations).as("repository reads that can return soft-deleted rows").isEmpty();
    }

    @Test
    void scannerSeesTheRepositories() throws Exception {
        assertThat(repositories()).extracting(Class::getSimpleName)
                .contains("TransactionRepository", "BudgetRepository", "UserRepository", "ExchangeRateRepository");
    }

    private static List<Class<?>> repositories() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition bd) {
                return bd.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.financetracker.api.repository")) {
            out.add(Class.forName(bd.getBeanClassName()));
        }
        return out;
    }
}
