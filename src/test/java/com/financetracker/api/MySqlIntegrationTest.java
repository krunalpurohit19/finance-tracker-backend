package com.financetracker.api;

import com.financetracker.api.dto.account.AccountDtos.CreateAccountRequest;
import com.financetracker.api.dto.auth.SignUpRequest;
import com.financetracker.api.dto.recurring.RecurringDtos.CreateRecurringRequest;
import com.financetracker.api.dto.transaction.TransactionDtos.CreateTransactionRequest;
import com.financetracker.api.dto.transaction.TransactionDtos.TransactionPage;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.enums.AccountType;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.entity.enums.Frequency;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.service.AccountService;
import com.financetracker.api.service.AuthService;
import com.financetracker.api.service.CategoryService;
import com.financetracker.api.service.RecurringService;
import com.financetracker.api.service.ReportService;
import com.financetracker.api.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The whole application against a real MySQL 8.4 (Testcontainers; skipped when Docker isn't running).
 * Covers what mocked repositories can't prove: the Flyway migrations and schema validation, GROUP BY
 * and keyset queries, CHECK constraints, the recurring unique key and optimistic locking.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-that-is-at-least-32-chars")
@Testcontainers(disabledWithoutDocker = true)
class MySqlIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static final LocalDate DAY = LocalDate.of(2026, 3, 9);

    @Autowired AuthService auth;
    @Autowired AccountService accounts;
    @Autowired CategoryService categories;
    @Autowired TransactionService transactions;
    @Autowired RecurringService recurring;
    @Autowired ReportService reports;
    @Autowired FinancialAccountRepository accountRepo;
    @Autowired JdbcTemplate jdbc;

    String userId;
    String bank;
    String cash;

    @BeforeEach
    void newUser() {
        SignUpRequest signUp = new SignUpRequest();
        signUp.setName("IT");
        signUp.setEmail(UUID.randomUUID() + "@example.test");
        signUp.setPassword("long-enough-password");
        userId = auth.signUp(signUp).getUser().getId();
        bank = account("Bank", "1000");
        cash = account("Cash", "0");
    }

    @Test
    void balancesAndReportsAddUp() {
        String food = categories.list(userId, CategoryKind.EXPENSE, false).get(0).id();
        tx(TransactionType.INCOME, "5000", bank, null, null);
        tx(TransactionType.EXPENSE, "500.25", bank, null, food);
        tx(TransactionType.EXPENSE, "99.75", bank, null, null); // uncategorised: the NULL group
        tx(TransactionType.TRANSFER, "400", bank, cash, null);

        assertThat(accounts.get(userId, bank).balance()).isEqualByComparingTo("5000.00"); // 1000 + 5000 − 600 − 400
        assertThat(accounts.get(userId, cash).balance()).isEqualByComparingTo("400");

        var dashboard = reports.getDashboard(userId, "custom", DAY, DAY);
        assertThat(dashboard.income()).isEqualByComparingTo("5000");
        assertThat(dashboard.expenses()).isEqualByComparingTo("600"); // the transfer is not an expense
        assertThat(dashboard.netWorth()).isEqualByComparingTo("5400");
        assertThat(dashboard.spendingByCategory()).hasSize(2);
        assertThat(dashboard.spendingByCategory().get(1).categoryName()).isEqualTo("Uncategorized");

        var analytics = reports.getAnalytics(userId, "custom", DAY, DAY);
        assertThat(analytics.monthly()).singleElement().satisfies(m -> assertThat(m.month()).isEqualTo("2026-03"));
        assertThat(analytics.byAccount()).singleElement().satisfies(a -> assertThat(a.transactionCount()).isEqualTo(4));
        assertThat(reports.getNetWorth(userId, 3).history()).hasSize(3);
    }

    @Test
    void pagingVisitsEveryTransactionExactlyOnce() {
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) created.add(tx(TransactionType.EXPENSE, "1", bank, null, null)); // same day
        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            TransactionPage page = transactions.list(userId, null, null, null, null, null, cursor, 2);
            page.items().forEach(t -> seen.add(t.id()));
            cursor = page.nextCursor();
        } while (cursor != null);
        assertThat(seen).hasSize(5).containsExactlyInAnyOrderElementsOf(created);
    }

    @Test
    void theDatabaseRefusesWhatTheApplicationNeverWrites() {
        // MySQL reports a CHECK violation as a generic error (3819), so match on the constraint name.
        String id = tx(TransactionType.EXPENSE, "10", bank, null, null);
        assertThatThrownBy(() -> jdbc.update("UPDATE transactions SET amount = 0 WHERE id = ?", id))
                .hasMessageContaining("chk_tx_amount_positive");
        assertThatThrownBy(() -> jdbc.update("UPDATE transactions SET transfer_account_id = ? WHERE id = ?", cash, id))
                .hasMessageContaining("chk_tx_transfer_shape"); // an expense with a second account
        assertThatThrownBy(() -> jdbc.update("UPDATE transactions SET fx_rate = 0 WHERE id = ?", id))
                .hasMessageContaining("chk_tx_fx_rate_positive");
        // ...but a negative opening balance is legitimate
        assertThat(jdbc.update("UPDATE financial_accounts SET opening_balance = -50 WHERE id = ?", bank)).isEqualTo(1);
    }

    @Test
    void aStaleEditIsRefused() {
        FinancialAccount stale = accountRepo.findById(bank).orElseThrow(); // detached: no transaction here
        jdbc.update("UPDATE financial_accounts SET name = 'Renamed elsewhere', version = version + 1 WHERE id = ?", bank);
        stale.setName("My edit");
        assertThatThrownBy(() -> accountRepo.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void recurringGenerationIsIdempotent() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata")); // the default user timezone
        recurring.create(userId, new CreateRecurringRequest("Milk", TransactionType.EXPENSE, new BigDecimal("30"), null,
                bank, null, null, null, null, Frequency.DAILY, 1, today.minusDays(2), null, null));
        assertThat(recurring.generate(userId).created()).isEqualTo(3);
        assertThat(recurring.generate(userId).created()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ? AND recurring_id IS NOT NULL",
                Integer.class, userId)).isEqualTo(3);
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private String account(String name, String opening) {
        return accounts.create(userId, new CreateAccountRequest(name, AccountType.BANK, null, "INR",
                new BigDecimal(opening), null, null, null, null, null)).id();
    }

    private String tx(TransactionType type, String amount, String from, String to, String categoryId) {
        return transactions.create(userId, new CreateTransactionRequest(type, new BigDecimal(amount), from, to, null,
                categoryId, DAY, null, null, null)).id();
    }
}
