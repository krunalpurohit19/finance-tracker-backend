package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> liveness() {
        long uptime = Math.round(
                (double) java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("status", "ok", "uptimeSeconds", uptime)));
    }

    @GetMapping("/ready")
    public ResponseEntity<?> readiness() {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
            return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("status", "ready", "database", "reachable")));
        } catch (Exception e) {
            var body = ApiEnvelope.Error.builder()
                    .error(ApiEnvelope.ErrorBody.builder()
                            .code("INTERNAL")
                            .message("The database is not reachable right now")
                            .build())
                    .build();
            return ResponseEntity.status(500).body(body);
        }
    }
}
