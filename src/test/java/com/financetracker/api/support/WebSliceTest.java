package com.financetracker.api.support;

import com.financetracker.api.entity.User;
import com.financetracker.api.repository.UserRepository;
import com.financetracker.api.security.JwtTokenProvider;
import com.financetracker.api.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base for {@code @WebMvcTest} controller slices: the real security chain, filters,
 * exception handler and Jackson config, with no database.
 *
 * Subclasses add {@code @WebMvcTest(XController.class)} and mock what the controller needs
 * with {@code @MockitoBean}. {@link JwtTokenProvider} and {@link UserRepository} are already
 * mocked here: stub the inherited fields, never re-declare them (that fails with
 * NoUniqueBeanDefinitionException from jwtAuthFilter).
 */
@Import({SecurityConfig.class, WebSliceTest.DistinctClientIps.class})
public abstract class WebSliceTest {

    protected static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired protected MockMvc mvc;

    // Required by JwtAuthFilter; requests authenticate via asUser() instead of a real token.
    @MockitoBean protected JwtTokenProvider jwtTokenProvider;
    @MockitoBean protected UserRepository userRepository;

    /** A user shaped like one JwtAuthFilter loads (non-null columns set). */
    protected static User testUser() {
        return User.builder().id(USER_ID).name("Test User").email("test@example.test")
                .createdAt(Instant.EPOCH).updatedAt(Instant.EPOCH).build();
    }

    /** Sets the principal the way JwtAuthFilter does. */
    protected static RequestPostProcessor asUser() {
        return asUser(testUser());
    }

    protected static RequestPostProcessor asUser(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    /**
     * RateLimitFilter keeps its buckets for the life of the cached test context, keyed by client IP.
     * MockMvc always sends from 127.0.0.1, so /api/auth/** tests would hit 429 after 10 requests
     * depending on test order. Give every request its own address; a test can still set its own.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class DistinctClientIps {
        private static final AtomicInteger counter = new AtomicInteger();

        @Bean
        MockMvcBuilderCustomizer distinctClientIps() {
            return builder -> builder.defaultRequest(get("/").with(request -> {
                int n = counter.incrementAndGet();
                request.setRemoteAddr("10." + (n >> 16 & 255) + "." + (n >> 8 & 255) + "." + (n & 255));
                return request;
            }));
        }
    }
}
