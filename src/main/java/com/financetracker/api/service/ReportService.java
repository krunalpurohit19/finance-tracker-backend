package com.financetracker.api.service;

import com.financetracker.api.dto.report.ReportDtos.*;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.AccountClass;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The read-only reports: dashboard, analytics and net worth. All amounts are in the base currency. */
@Service
public class ReportService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** DECIMAL(18,4) zero, as MySQL returns an empty SUM: "0.0000", not "0". */
    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final String UNCATEGORIZED = "Uncategorized";

    private final TransactionRepository txRepo;
    private final UserSettingsRepository settingsRepo;
    private final FinancialAccountRepository accountRepo;
    private final ExchangeRateRepository rateRepo;
    private final CategoryRepository categoryRepo;
    private final Clock clock;

    public ReportService(TransactionRepository txRepo, UserSettingsRepository settingsRepo,
                         FinancialAccountRepository accountRepo, ExchangeRateRepository rateRepo,
                         CategoryRepository categoryRepo, Clock clock) {
        this.txRepo = txRepo;
        this.settingsRepo = settingsRepo;
        this.accountRepo = accountRepo;
        this.rateRepo = rateRepo;
        this.categoryRepo = categoryRepo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String userId, String preset, LocalDate from, LocalDate to) {
        UserSettings settings = settings(userId);
        LocalDate today = UserDates.today(clock, settings);
        Period period = Periods.resolve(preset, from, to, today);

        BigDecimal[] sums = incomeAndExpense(userId, period.from(), period.to());
        BigDecimal savings = sums[0].subtract(sums[1]);
        BigDecimal[] lifetime = incomeAndExpense(userId, UserDates.MIN_DATE, UserDates.MAX_DATE);

        BigDecimal assets = ZERO;
        BigDecimal liabilities = ZERO;
        List<UnconvertedAccount> unconverted = new ArrayList<>();
        for (Balance b : balances(userId, settings.getBaseCurrency(), today)) {
            if (b.baseBalance() == null) unconverted.add(b.unconverted());
            else if (b.account().getAccountClass() == AccountClass.ASSET) assets = assets.add(b.baseBalance());
            else liabilities = liabilities.add(b.baseBalance());
        }
        BigDecimal netWorth = assets.add(liabilities);

        List<RecentTransaction> recent = txRepo
                .findFiltered(userId, null, null, null, period.from(), period.to(), PageRequest.of(0, 5)).stream()
                .map(t -> new RecentTransaction(t.getId(), t.getType(), t.getAmount(), t.getCurrency(), t.getBaseAmount(),
                        t.getCategory() != null ? t.getCategory().getId() : null,
                        t.getCategory() != null ? t.getCategory().getName() : null,
                        t.getAccount() != null ? t.getAccount().getName() : null, t.getMerchant(), t.getOccurredOn()))
                .toList();

        return new DashboardResponse(settings.getBaseCurrency(), period, netWorth, netWorth, assets, liabilities,
                lifetime[0].subtract(lifetime[1]), sums[0], sums[1], savings, Ratios.percent(savings, sums[0]),
                spendingByCategory(userId, period, categories(userId), true), unconverted, recent);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String userId, String preset, LocalDate from, LocalDate to) {
        UserSettings settings = settings(userId);
        Period period = Periods.resolve(preset, from, to, UserDates.today(clock, settings));
        DateRange prev = Periods.previous(period);

        BigDecimal[] sums = incomeAndExpense(userId, period.from(), period.to());
        BigDecimal net = sums[0].subtract(sums[1]);

        Map<String, Category> categories = categories(userId);
        Map<String, BigDecimal> previousByCategory = new HashMap<>();
        for (Object[] r : txRepo.sumByCategory(userId, TransactionType.EXPENSE, prev.from(), prev.to())) {
            previousByCategory.put((String) r[0], decimal(r[1]));
        }
        List<CategoryChange> changes = new ArrayList<>();
        for (Object[] r : txRepo.sumByCategory(userId, TransactionType.EXPENSE, period.from(), period.to())) {
            String id = (String) r[0];
            BigDecimal current = decimal(r[1]);
            BigDecimal previous = previousByCategory.getOrDefault(id, ZERO);
            BigDecimal change = current.subtract(previous);
            // No earlier spend to compare with: shown as +100% (existing app behaviour).
            BigDecimal changePercent = previous.signum() > 0 ? Ratios.percent(change, previous)
                    : current.signum() > 0 ? HUNDRED : null;
            Category c = categories.get(id);
            changes.add(new CategoryChange(id, c != null ? c.getName() : UNCATEGORIZED, c != null ? c.getColor() : null,
                    current, previous, change, changePercent));
        }
        changes.sort(Comparator.comparing(CategoryChange::change).reversed());

        List<MonthPoint> monthly = txRepo.monthlyTrend(userId, period.from(), period.to()).stream().map(r -> {
            BigDecimal income = decimal(r[1]);
            BigDecimal expenses = decimal(r[2]);
            BigDecimal monthNet = income.subtract(expenses);
            return new MonthPoint((String) r[0], income, expenses, monthNet, Ratios.percent(monthNet, income));
        }).toList();

        List<MerchantSpend> merchants = txRepo.topMerchants(userId, period.from(), period.to(), PageRequest.of(0, 10))
                .stream().map(r -> new MerchantSpend((String) r[0], decimal(r[1]), ((Number) r[2]).intValue())).toList();

        Map<String, FinancialAccount> accounts = accountRepo.findActiveByUserId(userId).stream()
                .collect(Collectors.toMap(FinancialAccount::getId, Function.identity()));
        List<AccountActivity> byAccount = new ArrayList<>();
        for (Object[] r : txRepo.sumByAccount(userId, period.from(), period.to())) {
            FinancialAccount a = accounts.get((String) r[0]);
            if (a == null) continue; // deleted account
            byAccount.add(new AccountActivity(a.getId(), a.getName(), a.getCurrency(), decimal(r[1]), decimal(r[2]),
                    ((Number) r[3]).intValue()));
        }

        return new AnalyticsResponse(settings.getBaseCurrency(), period, prev,
                new Totals(sums[0], sums[1], net, Ratios.percent(net, sums[0])), monthly,
                spendingByCategory(userId, period, categories, false), changes, merchants, byAccount);
    }

    @Transactional(readOnly = true)
    public NetWorthResponse getNetWorth(String userId, int months) {
        UserSettings settings = settings(userId);
        LocalDate today = UserDates.today(clock, settings);

        List<Balance> balances = balances(userId, settings.getBaseCurrency(), today);
        BigDecimal assets = ZERO;
        BigDecimal liabilities = ZERO;
        for (Balance b : balances) {
            if (b.baseBalance() == null) continue;
            if (b.account().getAccountClass() == AccountClass.ASSET) assets = assets.add(b.baseBalance());
            else liabilities = liabilities.add(b.baseBalance());
        }
        BigDecimal totalAssets = assets;
        BigDecimal totalLiabilities = liabilities;
        List<NetWorthAccount> assetAccounts = balances.stream()
                .filter(b -> b.account().getAccountClass() == AccountClass.ASSET)
                .map(b -> b.netWorthAccount(totalAssets))
                .sorted(Comparator.comparing(NetWorthAccount::baseBalance).reversed()).toList();
        List<NetWorthAccount> liabilityAccounts = balances.stream()
                .filter(b -> b.account().getAccountClass() != AccountClass.ASSET)
                .map(b -> b.netWorthAccount(totalLiabilities))
                .sorted(Comparator.comparing(NetWorthAccount::baseBalance)).toList();
        BigDecimal netWorth = assets.add(liabilities);

        // ponytail: history rewinds today's net worth by each month's income − expenses (base currency).
        // It ignores opening balances added later and FX drift, and can't split assets from liabilities;
        // month-end balance snapshots if the chart ever needs to be exact.
        YearMonth currentMonth = YearMonth.from(today);
        Map<String, BigDecimal> netByMonth = new HashMap<>();
        for (Object[] r : txRepo.monthlyTrend(userId, currentMonth.minusMonths(months).atDay(1), currentMonth.atEndOfMonth())) {
            netByMonth.put((String) r[0], decimal(r[1]).subtract(decimal(r[2])));
        }
        BigDecimal[] values = new BigDecimal[months];
        BigDecimal running = netWorth;
        for (int i = 0; i < months; i++) {
            values[months - 1 - i] = running;
            running = running.subtract(netByMonth.getOrDefault(currentMonth.minusMonths(i).toString(), ZERO));
        }
        List<NetWorthPoint> history = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            BigDecimal v = values[i];
            history.add(new NetWorthPoint(currentMonth.minusMonths(months - 1 - i).toString(),
                    v.signum() > 0 ? v : ZERO, v.signum() < 0 ? v : ZERO, v,
                    i > 0 ? v.subtract(values[i - 1]) : null));
        }
        BigDecimal monthChange = months >= 2 ? values[months - 1].subtract(values[months - 2]) : ZERO;
        BigDecimal monthChangePercent = months >= 2 ? Ratios.percent(monthChange, values[months - 2]) : null;

        return new NetWorthResponse(settings.getBaseCurrency(), today, netWorth, assets, liabilities, monthChange,
                monthChangePercent, history, assetAccounts, liabilityAccounts,
                balances.stream().filter(b -> b.baseBalance() == null).map(Balance::unconverted).toList());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** An account's balance in its own currency and in the base currency ({@code null}: no exchange rate). */
    private record Balance(FinancialAccount account, BigDecimal balance, BigDecimal baseBalance) {

        UnconvertedAccount unconverted() {
            return new UnconvertedAccount(account.getId(), account.getName(), account.getCurrency(), balance);
        }

        NetWorthAccount netWorthAccount(BigDecimal classTotal) {
            BigDecimal base = baseBalance != null ? baseBalance : ZERO;
            // Liabilities are negative on both sides, so the share is still a positive percentage.
            BigDecimal share = classTotal.signum() == 0 ? ZERO
                    : base.multiply(HUNDRED).divide(classTotal, 2, RoundingMode.HALF_UP);
            return new NetWorthAccount(account.getId(), account.getName(), account.getType(), account.getCurrency(),
                    account.getArchivedAt() != null, balance, base, baseBalance != null, share);
        }
    }

    // ponytail: one movement query (and, for foreign accounts, one rate query) per account. Fine for a
    // handful of accounts; a GROUP BY account query if someone has dozens.
    private List<Balance> balances(String userId, String baseCurrency, LocalDate today) {
        List<Balance> result = new ArrayList<>();
        for (FinancialAccount a : accountRepo.findActiveByUserId(userId)) {
            BigDecimal movement = txRepo.computeAccountMovement(a.getId());
            BigDecimal balance = a.getOpeningBalance().add(movement != null ? movement : ZERO);
            BigDecimal base;
            if (a.getCurrency().equals(baseCurrency)) {
                base = balance;
            } else {
                Optional<ExchangeRate> rate = rateRepo.findEffectiveRate(userId, a.getCurrency(), baseCurrency, today);
                base = rate.map(r -> balance.multiply(r.getRate()).setScale(4, RoundingMode.HALF_UP)).orElse(null);
            }
            result.add(new Balance(a, balance, base));
        }
        return result;
    }

    /** Expenses per category, largest first, each with its share of the period's categorised spending. */
    private List<CategorySpend> spendingByCategory(String userId, Period period, Map<String, Category> categories,
                                                   boolean withIcon) {
        List<Object[]> rows = txRepo.sumByCategory(userId, TransactionType.EXPENSE, period.from(), period.to());
        BigDecimal total = rows.stream().map(r -> decimal(r[1])).reduce(ZERO, BigDecimal::add);
        return rows.stream().map(r -> {
            Category c = categories.get((String) r[0]);
            BigDecimal amount = decimal(r[1]);
            BigDecimal share = Ratios.percent(amount, total);
            return new CategorySpend((String) r[0], c != null ? c.getName() : UNCATEGORIZED,
                    c != null ? c.getColor() : null, withIcon && c != null ? c.getIcon() : null, amount,
                    share != null ? share : ZERO);
        }).sorted(Comparator.comparing(CategorySpend::total).reversed()).toList();
    }

    private Map<String, Category> categories(String userId) {
        return categoryRepo.findActiveByUserId(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    /** {income, expenses} over an inclusive date range. */
    private BigDecimal[] incomeAndExpense(String userId, LocalDate from, LocalDate to) {
        List<Object[]> rows = txRepo.sumIncomeAndExpense(userId, from, to);
        if (rows.isEmpty()) return new BigDecimal[]{ZERO, ZERO};
        return new BigDecimal[]{decimal(rows.get(0)[0]), decimal(rows.get(0)[1])};
    }

    private UserSettings settings(String userId) {
        return settingsRepo.findById(userId).orElseThrow(() -> ApiException.notFound("Settings not found"));
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
    }

    static long daysInclusive(Period p) {
        return ChronoUnit.DAYS.between(p.from(), p.to()) + 1;
    }
}
