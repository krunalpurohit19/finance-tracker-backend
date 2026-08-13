package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/net-worth")
public class NetWorthController {

    private final FinancialAccountRepository accountRepo;
    private final TransactionRepository txRepo;
    private final ExchangeRateRepository rateRepo;
    private final UserSettingsRepository settingsRepo;

    public NetWorthController(FinancialAccountRepository accountRepo, TransactionRepository txRepo,
                               ExchangeRateRepository rateRepo, UserSettingsRepository settingsRepo) {
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.rateRepo = rateRepo;
        this.settingsRepo = settingsRepo;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> netWorth() {
        String userId = SecurityUtils.currentUserId();
        var settings = settingsRepo.findById(userId).orElseThrow();
        String baseCurrency = settings.getBaseCurrency();

        List<FinancialAccount> accounts = accountRepo.findActiveByUserId(userId);
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        List<Map<String, Object>> accountBreakdown = new ArrayList<>();

        for (FinancialAccount a : accounts) {
            BigDecimal movement = txRepo.computeAccountMovement(a.getId());
            BigDecimal balance = a.getOpeningBalance().add(movement != null ? movement : BigDecimal.ZERO);

            BigDecimal baseBalance;
            if (a.getCurrency().equals(baseCurrency)) {
                baseBalance = balance;
            } else {
                BigDecimal rate = rateRepo.findEffectiveRate(userId, a.getCurrency(), baseCurrency, LocalDate.now())
                        .map(ExchangeRate::getRate).orElse(BigDecimal.ONE);
                baseBalance = balance.multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP);
            }

            switch (a.getAccountClass()) {
                case ASSET -> totalAssets = totalAssets.add(baseBalance);
                case LIABILITY -> totalLiabilities = totalLiabilities.add(baseBalance);
            }

            accountBreakdown.add(Map.of(
                    "id", a.getId(),
                    "name", a.getName(),
                    "type", a.getType().name(),
                    "class", a.getAccountClass().name(),
                    "currency", a.getCurrency(),
                    "balance", balance.toPlainString(),
                    "baseBalance", baseBalance.toPlainString()
            ));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseCurrency", baseCurrency);
        data.put("totalAssets", totalAssets.toPlainString());
        data.put("totalLiabilities", totalLiabilities.toPlainString());
        data.put("netWorth", totalAssets.add(totalLiabilities).toPlainString());
        data.put("accounts", accountBreakdown);
        return ResponseEntity.ok(new ApiEnvelope.Success<>(data));
    }
}
