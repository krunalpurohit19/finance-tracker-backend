package com.financetracker.api.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With open-in-view off, a service method without a transaction mutates a detached entity and the
 * change is silently lost; a readOnly one never flushes. Mocked-repository tests can't see either,
 * so pin the declarations: every public method is @Transactional, queries readOnly, commands not.
 * Services join this list as their module is hardened.
 */
class ServiceTransactionsTest {

    static final Set<String> QUERY_PREFIXES = Set.of("get", "list", "history", "me");

    @ParameterizedTest
    @ValueSource(classes = {SettingsService.class, ExchangeRateService.class, BudgetService.class})
    void everyPublicMethodDeclaresItsTransaction(Class<?> service) {
        for (Method m : service.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
            Transactional tx = m.getAnnotation(Transactional.class);
            assertThat(tx).as("%s.%s must be @Transactional", service.getSimpleName(), m.getName()).isNotNull();
            boolean query = QUERY_PREFIXES.stream().anyMatch(p -> m.getName().equals(p) || m.getName().startsWith(p) && Character.isUpperCase(m.getName().charAt(p.length())));
            assertThat(tx.readOnly()).as("%s.%s readOnly", service.getSimpleName(), m.getName()).isEqualTo(query);
        }
        assertThat(Arrays.stream(service.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))).isNotEmpty();
    }
}
