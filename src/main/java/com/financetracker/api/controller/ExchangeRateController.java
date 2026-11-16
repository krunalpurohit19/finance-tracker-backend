package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.IdResponse;
import com.financetracker.api.dto.exchangerate.ExchangeRateDtos.ExchangeRateResponse;
import com.financetracker.api.dto.exchangerate.ExchangeRateDtos.UpsertExchangeRateRequest;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.ExchangeRateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService rateService;

    public ExchangeRateController(ExchangeRateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping
    public ApiEnvelope.Success<List<ExchangeRateResponse>> list() {
        return new ApiEnvelope.Success<>(rateService.list(SecurityUtils.currentUserId()));
    }

    @PutMapping
    public ApiEnvelope.Success<ExchangeRateResponse> upsert(@Valid @RequestBody UpsertExchangeRateRequest body) {
        return new ApiEnvelope.Success<>(rateService.upsert(SecurityUtils.currentUserId(), body));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope.Success<IdResponse> remove(@PathVariable String id) {
        rateService.remove(SecurityUtils.currentUserId(), id);
        return new ApiEnvelope.Success<>(new IdResponse(id));
    }
}
