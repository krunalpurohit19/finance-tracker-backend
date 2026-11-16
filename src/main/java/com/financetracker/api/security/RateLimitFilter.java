package com.financetracker.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.api.dto.ApiEnvelope;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Use Caffeine cache to automatically evict old IPs and prevent OutOfMemory (OOM) attacks
    private final Cache<String, Bucket> cache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5)) // Evict after 5 minutes of inactivity
            .maximumSize(10_000) // Keep at most 10,000 IPs in memory
            .build();
            
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Sign-in/sign-up guesses per minute, per IP. */
    static final int AUTH_PER_MINUTE = 10;
    /**
     * Refreshes per minute, per IP. Counted apart from sign-in: refresh needs a valid token (nothing to
     * guess), and every phone behind one office or carrier NAT refreshes hourly from the same address.
     * Sharing the sign-in budget would lock users out of signing in, or sign them out.
     */
    static final int REFRESH_PER_MINUTE = 60;

    private Bucket resolveBucket(String ip, boolean refresh) {
        int perMinute = refresh ? REFRESH_PER_MINUTE : AUTH_PER_MINUTE;
        return cache.get((refresh ? "refresh:" : "auth:") + ip, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1)))).build());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only rate limit auth endpoints
        // Everything that takes a password or a code from an anonymous caller.
        if (request.getRequestURI().startsWith("/api/auth/")
                || request.getRequestURI().equals("/delete-account") && "POST".equals(request.getMethod())) {
            // The connection's address. Behind a proxy, set FORWARD_HEADERS=native so Tomcat fills this from
            // X-Forwarded-For for trusted proxies only; reading the header here would let anyone pick their
            // own bucket and never be limited.
            String ip = request.getRemoteAddr();

            Bucket bucket = resolveBucket(ip, request.getRequestURI().equals("/api/auth/refresh"));

            if (!bucket.tryConsume(1)) {
                response.setStatus(429); // Too Many Requests
                response.setContentType("application/json");
                var body = ApiEnvelope.Error.builder()
                        .error(ApiEnvelope.ErrorBody.builder()
                                .code("TOO_MANY_REQUESTS")
                                .message("Rate limit exceeded. Please try again later.")
                                .build())
                        .build();
                objectMapper.writeValue(response.getOutputStream(), body);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
