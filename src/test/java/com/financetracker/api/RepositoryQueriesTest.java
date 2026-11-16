package com.financetracker.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the full application context against NO database: Hibernate is told not to touch JDBC
 * metadata and the pool not to connect at startup. Spring Data still parses every derived query
 * and validates every JPQL {@code @Query} while creating the repositories, so a typo'd property
 * or bad JPQL anywhere fails this test — without Docker. Execution against MySQL is Testcontainers' job.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "app.jwt.secret=test-only-secret-that-is-at-least-32-chars",
        "spring.data.jpa.repositories.bootstrap-mode=default", // lazy repositories would make this test vacuous
})
class RepositoryQueriesTest {

    @Test
    void everyRepositoryQueryParses() {
        // Context startup is the assertion.
    }
}
