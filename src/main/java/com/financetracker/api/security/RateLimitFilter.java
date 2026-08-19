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

    private Bucket resolveBucket(String ip) {
        return cache.get(ip, this::newBucket);
    }

    private Bucket newBucket(String ip) {
        // Limit to 10 requests per minute for auth endpoints
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only rate limit auth endpoints
        if (request.getRequestURI().startsWith("/api/auth/")) {
            String ip = request.getRemoteAddr();
            // Get IP from reverse proxy if behind one
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                ip = forwardedFor.split(",")[0].trim();
            }

            Bucket bucket = resolveBucket(ip);

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
