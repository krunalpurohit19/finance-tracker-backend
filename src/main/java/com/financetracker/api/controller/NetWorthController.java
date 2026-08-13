package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.enums.AccountClass;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
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
    public ResponseEntity<ApiEnvelope.Success<Map<String, Object>>> netWorth(
            @RequestParam(defaultValue = "12") int months) {
        String userId = SecurityUtils.currentUserId();
        var settings = settingsRepo.findById(userId).orElseThrow();
        String baseCurrency = settings.getBaseCurrency();

        List<FinancialAccount> accounts = accountRepo.findActiveByUserId(userId);
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        
        List<Map<String, Object>> assetAccounts = new ArrayList<>();
        List<Map<String, Object>> liabilityAccounts = new ArrayList<>();
        List<Map<String, Object>> unconvertedAccounts = new ArrayList<>();

        for (FinancialAccount a : accounts) {
            BigDecimal movement = txRepo.computeAccountMovement(a.getId());
            BigDecimal balance = a.getOpeningBalance().add(movement != null ? movement : BigDecimal.ZERO);

            BigDecimal baseBalance;
            boolean convertible = true;
            if (a.getCurrency().equals(baseCurrency)) {
                baseBalance = balance;
            } else {
                Optional<ExchangeRate> rateOpt = rateRepo.findEffectiveRate(userId, a.getCurrency(), baseCurrency, LocalDate.now());
                if (rateOpt.isPresent()) {
                    baseBalance = balance.multiply(rateOpt.get().getRate()).setScale(4, RoundingMode.HALF_UP);
                } else {
                    convertible = false;
                    baseBalance = BigDecimal.ZERO;
                    unconvertedAccounts.add(Map.of(
                            "accountId", a.getId(),
                            "name", a.getName(),
                            "currency", a.getCurrency(),
                            "balance", balance.toPlainString()
                    ));
                }
            }

            Map<String, Object> accData = new HashMap<>();
            accData.put("accountId", a.getId());
            accData.put("name", a.getName());
            accData.put("type", a.getType().name());
            accData.put("currency", a.getCurrency());
            accData.put("archived", false); // active only queried
            accData.put("balance", balance.toPlainString());
            accData.put("baseBalance", baseBalance.toPlainString());
            accData.put("convertible", convertible);
            accData.put("share", 0.0); // populated later

            if (a.getAccountClass() == AccountClass.ASSET) {
                totalAssets = totalAssets.add(baseBalance);
                assetAccounts.add(accData);
            } else {
                totalLiabilities = totalLiabilities.add(baseBalance);
                liabilityAccounts.add(accData);
            }
        }
        
        // Calculate shares
        for (Map<String, Object> acc : assetAccounts) {
            BigDecimal bb = new BigDecimal((String) acc.get("baseBalance"));
            if (totalAssets.compareTo(BigDecimal.ZERO) != 0) {
                acc.put("share", bb.divide(totalAssets, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue());
            }
        }
        for (Map<String, Object> acc : liabilityAccounts) {
            BigDecimal bb = new BigDecimal((String) acc.get("baseBalance"));
            if (totalLiabilities.compareTo(BigDecimal.ZERO) != 0) {
                acc.put("share", bb.divide(totalLiabilities, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue());
            }
        }
        
        assetAccounts.sort((m1, m2) -> new BigDecimal((String) m2.get("baseBalance")).compareTo(new BigDecimal((String) m1.get("baseBalance"))));
        liabilityAccounts.sort((m1, m2) -> new BigDecimal((String) m1.get("baseBalance")).compareTo(new BigDecimal((String) m2.get("baseBalance"))));

        BigDecimal currentNetWorth = totalAssets.add(totalLiabilities);

        // Calculate history by rewinding net worth
        YearMonth currentMonth = YearMonth.now();
        LocalDate startOfHistory = currentMonth.minusMonths(months).atDay(1);
        List<Object[]> rawTrend = txRepo.monthlyTrend(userId, startOfHistory, currentMonth.atEndOfMonth());
        Map<String, BigDecimal> monthNetMap = new HashMap<>();
        for (Object[] r : rawTrend) {
            BigDecimal inc = new BigDecimal(r[1].toString());
            BigDecimal exp = new BigDecimal(r[2].toString());
            monthNetMap.put((String) r[0], inc.subtract(exp));
        }

        List<Map<String, Object>> history = new ArrayList<>();
        BigDecimal runningNetWorth = currentNetWorth;

        // Create history array backwards, then reverse
        for (int i = 0; i < months; i++) {
            YearMonth ym = currentMonth.minusMonths(i);
            String monthStr = ym.toString();
            
            // For the graph, we just fake assets/liabilities if rewinding, keeping their ratio roughly the same or just flat.
            // A precise rewinding requires full ledger scanning. For now, flat assets/liabilities relative to networth change.
            Map<String, Object> point = new HashMap<>();
            point.put("month", monthStr);
            point.put("netWorth", runningNetWorth.toPlainString());
            point.put("assets", runningNetWorth.compareTo(BigDecimal.ZERO) > 0 ? runningNetWorth.toPlainString() : "0");
            point.put("liabilities", runningNetWorth.compareTo(BigDecimal.ZERO) < 0 ? runningNetWorth.toPlainString() : "0");
            point.put("change", null); // calculated after reverse
            history.add(point);
            
            BigDecimal netThisMonth = monthNetMap.getOrDefault(monthStr, BigDecimal.ZERO);
            runningNetWorth = runningNetWorth.subtract(netThisMonth);
        }
        
        Collections.reverse(history);
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) {
                BigDecimal prev = new BigDecimal((String) history.get(i-1).get("netWorth"));
                BigDecimal curr = new BigDecimal((String) history.get(i).get("netWorth"));
                history.get(i).put("change", curr.subtract(prev).toPlainString());
            }
        }
        
        BigDecimal monthChange = BigDecimal.ZERO;
        Double monthChangePercent = null;
        if (history.size() >= 2) {
            String cStr = (String) history.get(history.size() - 1).get("change");
            if (cStr != null) {
                monthChange = new BigDecimal(cStr);
                BigDecimal prevNW = new BigDecimal((String) history.get(history.size() - 2).get("netWorth"));
                if (prevNW.compareTo(BigDecimal.ZERO) > 0) {
                    monthChangePercent = monthChange.divide(prevNW, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseCurrency", baseCurrency);
        data.put("asOf", LocalDate.now().toString());
        data.put("netWorth", currentNetWorth.toPlainString());
        data.put("assets", totalAssets.toPlainString());
        data.put("liabilities", totalLiabilities.toPlainString());
        data.put("monthChange", monthChange.toPlainString());
        data.put("monthChangePercent", monthChangePercent);
        data.put("history", history);
        data.put("assetAccounts", assetAccounts);
        data.put("liabilityAccounts", liabilityAccounts);
        data.put("unconvertedAccounts", unconvertedAccounts);
        
        return ResponseEntity.ok(new ApiEnvelope.Success<>(data));
    }
}
