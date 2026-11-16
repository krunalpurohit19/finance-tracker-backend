package com.financetracker.api.service;

import com.financetracker.api.dto.exchangerate.ExchangeRateDtos.ExchangeRateResponse;
import com.financetracker.api.dto.exchangerate.ExchangeRateDtos.UpsertExchangeRateRequest;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExchangeRateService {

    private final ExchangeRateRepository rateRepo;
    private final UserRepository userRepo;

    public ExchangeRateService(ExchangeRateRepository rateRepo, UserRepository userRepo) {
        this.rateRepo = rateRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> list(String userId) {
        return rateRepo.findActiveByUserId(userId).stream().map(ExchangeRateResponse::of).toList();
    }

    /** One rate per user, pair and effective date: an existing one is overwritten, a deleted one revived. */
    @Transactional
    public ExchangeRateResponse upsert(String userId, UpsertExchangeRateRequest req) {
        if (req.fromCurrency().equals(req.toCurrency())) {
            throw ApiException.validationFailed("Some of the details you entered need fixing",
                    Map.of("toCurrency", List.of("Choose two different currencies")));
        }
        ExchangeRate rate = rateRepo.findByUserIdAndFromCurrencyAndToCurrencyAndEffectiveFrom(
                        userId, req.fromCurrency(), req.toCurrency(), req.effectiveFrom())
                .orElseGet(() -> ExchangeRate.builder()
                        .id(UUID.randomUUID().toString())
                        .user(userRepo.getReferenceById(userId))
                        .fromCurrency(req.fromCurrency())
                        .toCurrency(req.toCurrency())
                        .effectiveFrom(req.effectiveFrom())
                        .build());
        rate.setRate(req.rate().setScale(8)); // DECIMAL(18,8); @Digits guarantees this is exact
        rate.setDeletedAt(null);
        return ExchangeRateResponse.of(rateRepo.save(rate));
    }

    @Transactional
    public void remove(String userId, String id) {
        ExchangeRate rate = rateRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Exchange rate not found"));
        rate.setDeletedAt(Instant.now());
    }
}
