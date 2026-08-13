package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.User;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateRepository rateRepo;

    public ExchangeRateController(ExchangeRateRepository rateRepo) {
        this.rateRepo = rateRepo;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<List<Map<String, Object>>>> list() {
        String userId = SecurityUtils.currentUserId();
        List<ExchangeRate> rates = rateRepo.findActiveByUserId(userId);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(rates.stream().map(this::toMap).toList()));
    }

    @PutMapping
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> upsert(@RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.currentUserId();
        String from = ((String) body.get("fromCurrency")).toUpperCase();
        String to = ((String) body.get("toCurrency")).toUpperCase();
        LocalDate effectiveFrom = LocalDate.parse((String) body.get("effectiveFrom"));
        BigDecimal rate = new BigDecimal((String) body.get("rate"));

        ExchangeRate existing = rateRepo.findByUserIdAndFromCurrencyAndToCurrencyAndEffectiveFromAndDeletedAtIsNull(
                userId, from, to, effectiveFrom).orElse(null);

        if (existing != null) {
            existing.setRate(rate);
            rateRepo.save(existing);
            return ResponseEntity.ok(new ApiEnvelope.Success<>(toMap(existing)));
        }

        ExchangeRate newRate = ExchangeRate.builder()
                .id(UUID.randomUUID().toString())
                .user(User.builder().id(userId).build())
                .fromCurrency(from)
                .toCurrency(to)
                .rate(rate)
                .effectiveFrom(effectiveFrom)
                .build();
        rateRepo.save(newRate);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(toMap(newRate)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> remove(@PathVariable String id) {
        String userId = SecurityUtils.currentUserId();
        ExchangeRate rate = rateRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Exchange rate not found"));
        rate.setDeletedAt(Instant.now());
        rateRepo.save(rate);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(Map.of("id", id)));
    }

    private Map<String, Object> toMap(ExchangeRate r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("fromCurrency", r.getFromCurrency());
        m.put("toCurrency", r.getToCurrency());
        m.put("rate", r.getRate().toPlainString());
        m.put("effectiveFrom", r.getEffectiveFrom().toString());
        return m;
    }
}
