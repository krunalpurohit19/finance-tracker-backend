package com.financetracker.api.controller;

import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.ExchangeRate;
import com.financetracker.api.entity.FinancialAccount;
import com.financetracker.api.entity.Transaction;
import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.entity.enums.AccountClass;
import com.financetracker.api.entity.enums.AccountType;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.entity.enums.TransactionSource;
import com.financetracker.api.entity.enums.TransactionType;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.ExchangeRateRepository;
import com.financetracker.api.repository.FinancialAccountRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserSettingsRepository;
import com.financetracker.api.service.AccountService;
import com.financetracker.api.service.CategoryService;
import com.financetracker.api.service.TransactionService;
import com.financetracker.api.support.WebSliceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pins the JSON contract of /accounts, /categories and /transactions. Bodies are compared STRICTLY. */
@WebMvcTest({AccountController.class, CategoryController.class, TransactionController.class})
@Import({AccountService.class, CategoryService.class, TransactionService.class})
class LedgerApiContractTest extends WebSliceTest {

    @MockitoBean FinancialAccountRepository accountRepo;
    @MockitoBean TransactionRepository txRepo;
    @MockitoBean CategoryRepository categoryRepo;
    @MockitoBean ExchangeRateRepository rateRepo;
    @MockitoBean UserSettingsRepository settingsRepo;

    FinancialAccount bank, card, dollars;
    Category food, salary;

    @BeforeEach
    void setUp() {
        when(settingsRepo.findById(USER_ID)).thenReturn(Optional.of(UserSettings.builder().userId(USER_ID).build()));
        when(settingsRepo.findForShare(USER_ID)).thenReturn(Optional.of(UserSettings.builder().userId(USER_ID).build()));
        bank = FinancialAccount.builder().id("a-bank").name("Bank").type(AccountType.BANK).accountClass(AccountClass.ASSET)
                .currency("INR").openingBalance(new BigDecimal("1000.0000")).institution("HDFC").last4("1234")
                .color("#112233").icon("bank").isDefault(true).createdAt(Instant.EPOCH).build();
        card = FinancialAccount.builder().id("a-card").name("Card").type(AccountType.CREDIT_CARD)
                .accountClass(AccountClass.LIABILITY).currency("INR").openingBalance(new BigDecimal("0.0000"))
                .sortOrder(1).archivedAt(Instant.EPOCH).createdAt(Instant.EPOCH).build();
        dollars = FinancialAccount.builder().id("a-usd").name("Dollars").type(AccountType.WALLET)
                .accountClass(AccountClass.ASSET).currency("USD").openingBalance(new BigDecimal("0.0000"))
                .createdAt(Instant.EPOCH).build();
        for (FinancialAccount a : List.of(bank, card, dollars)) {
            when(accountRepo.findByIdAndUserIdAndDeletedAtIsNull(a.getId(), USER_ID)).thenReturn(Optional.of(a));
        }
        when(txRepo.computeAccountMovement("a-bank")).thenReturn(new BigDecimal("-250.5000"));
        when(txRepo.computeAccountMovement("a-card")).thenReturn(new BigDecimal("0.0000"));
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        food = Category.builder().id("c-food").name("Food").kind(CategoryKind.EXPENSE).color("#ff0000").icon("restaurant")
                .isSystem(true).build();
        salary = Category.builder().id("c-sal").name("Salary").kind(CategoryKind.INCOME).sortOrder(2)
                .archivedAt(Instant.EPOCH).parent(food).build();
        for (Category c : List.of(food, salary)) {
            when(categoryRepo.findByIdAndUserIdAndDeletedAtIsNull(c.getId(), USER_ID)).thenReturn(Optional.of(c));
        }
        when(categoryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── accounts ─────────────────────────────────────────────────────────

    @Test
    void accountsList() throws Exception {
        when(accountRepo.findActiveByUserId(USER_ID)).thenReturn(List.of(bank, card));
        expect(mvc.perform(get("/api/v1/accounts?includeArchived=true").with(asUser())), 200, "accountsList");
    }

    @Test
    void accountCreate() throws Exception {
        var r = send(post("/api/v1/accounts"), """
                {"name":"Wallet","type":"CREDIT_CARD","currency":"inr","openingBalance":"-12.5","last4":"9876","isDefault":false}""");
        var saved = ArgumentCaptor.forClass(FinancialAccount.class);
        verify(accountRepo).save(saved.capture());
        expect(r, 201, "accountCreate", saved.getValue().getId());
    }

    @Test
    void accountPatchArchiveDeleteReorder() throws Exception {
        expect(send(patch("/api/v1/accounts/a-bank"), "{\"name\":\"Main\",\"openingBalance\":\"5\"}"), 200, "accountPatch");
        expect(mvc.perform(post("/api/v1/accounts/a-bank/archive").with(asUser())), 200, "accountArchive",
                bank.getArchivedAt().toString());
        expect(mvc.perform(post("/api/v1/accounts/a-card/unarchive").with(asUser())), 200, "accountUnarchive");
        expect(mvc.perform(delete("/api/v1/accounts/a-card").with(asUser())), 200, "accountDelete");
        assertThat(card.getDeletedAt()).isNotNull();
        expect(send(post("/api/v1/accounts/reorder"), "{\"orderedIds\":[\"a-card\",\"a-bank\"]}"), 200, "accountReorder");
        assertThat(bank.getSortOrder()).isEqualTo(1);
        when(accountRepo.countTransactionsByAccountId("a-bank")).thenReturn(1L);
        expect(mvc.perform(delete("/api/v1/accounts/a-bank").with(asUser())), 409, "accountInUse");
        expect(mvc.perform(get("/api/v1/accounts/nope").with(asUser())), 404, "accountMissing");
    }

    // ── categories ───────────────────────────────────────────────────────

    @Test
    void categories() throws Exception {
        when(categoryRepo.findActiveByUserId(USER_ID)).thenReturn(List.of(food, salary));
        when(categoryRepo.findActiveByUserIdAndKind(USER_ID, CategoryKind.INCOME)).thenReturn(List.of(salary));
        expect(mvc.perform(get("/api/v1/categories?includeArchived=true").with(asUser())), 200, "categoriesAll");
        expect(mvc.perform(get("/api/v1/categories").with(asUser())), 200, "categoriesActive");
        expect(mvc.perform(get("/api/v1/categories?kind=INCOME").with(asUser())), 200, "categoriesIncome");
        expect(mvc.perform(get("/api/v1/categories/c-food").with(asUser())), 200, "categoryGet");
        expect(send(patch("/api/v1/categories/c-food"), "{\"name\":\"Eating out\",\"color\":\"#00ff00\"}"), 200, "categoryPatch");
        expect(mvc.perform(post("/api/v1/categories/c-food/archive").with(asUser())), 200, "categoryArchive",
                food.getArchivedAt().toString());
        expect(mvc.perform(post("/api/v1/categories/c-sal/unarchive").with(asUser())), 200, "categoryUnarchive");
        expect(mvc.perform(delete("/api/v1/categories/c-sal").with(asUser())), 200, "categoryDelete");
        when(categoryRepo.countTransactionsByCategoryId("c-food")).thenReturn(2L);
        expect(mvc.perform(delete("/api/v1/categories/c-food").with(asUser())), 409, "categoryInUse");
    }

    @Test
    void categoryCreate() throws Exception {
        var r = send(post("/api/v1/categories"), "{\"name\":\"Pets\",\"kind\":\"EXPENSE\",\"icon\":\"paw\",\"parentId\":\"c-food\"}");
        var saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepo).save(saved.capture());
        expect(r, 201, "categoryCreate", saved.getValue().getId());
    }

    // ── transactions ─────────────────────────────────────────────────────

    private Transaction tx(String id) {
        return Transaction.builder().id(id).type(TransactionType.EXPENSE).amount(new BigDecimal("500.0000")).currency("INR")
                .baseAmount(new BigDecimal("500.0000")).baseCurrency("INR").fxRate(new BigDecimal("1.00000000")).account(bank)
                .category(food).occurredOn(LocalDate.of(2026, 3, 9)).merchant("Cafe").notes("lunch")
                .source(TransactionSource.MANUAL).createdAt(Instant.EPOCH).build();
    }

    @Test
    void transactionsListGetDeleteDuplicate() throws Exception {
        Transaction t1 = tx("t1");
        Transaction t2 = tx("t2");
        t2.setType(TransactionType.TRANSFER);
        t2.setCategory(null);
        t2.setTransferAccount(dollars);
        t2.setTransferAmount(new BigDecimal("6.0000"));
        t2.setTransferCurrency("USD");
        t2.setMerchant(null);
        t2.setNotes(null);
        when(txRepo.findPage(eq(USER_ID), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(t1, t2, tx("t3")));
        when(txRepo.findByIdAndUserIdAndDeletedAtIsNull("t1", USER_ID)).thenReturn(Optional.of(t1));
        expect(mvc.perform(get("/api/v1/transactions?limit=2").with(asUser())), 200, "txList");
        expect(mvc.perform(get("/api/v1/transactions/t1").with(asUser())), 200, "txGet");
        var r = send(post("/api/v1/transactions/t1/duplicate"), "{\"occurredOn\":\"2026-04-01\"}");
        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(txRepo).save(saved.capture());
        expect(r, 201, "txDuplicate", saved.getValue().getId());
        expect(send(patch("/api/v1/transactions/t1"), "{\"amount\":\"750.25\",\"categoryId\":null,\"notes\":\"dinner\"}"), 200, "txPatch");
        expect(mvc.perform(delete("/api/v1/transactions/t1").with(asUser())), 200, "txDelete");
        assertThat(t1.getDeletedAt()).isNotNull();
        expect(mvc.perform(get("/api/v1/transactions/nope").with(asUser())), 404, "txMissing");
    }

    @Test
    void transactionCreateExpense() throws Exception {
        var r = send(post("/api/v1/transactions"), """
                {"type":"EXPENSE","amount":"120.5000","accountId":"a-bank","categoryId":"c-food","occurredOn":"2026-03-09",
                 "merchant":"Cafe"}""");
        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(txRepo).save(saved.capture());
        expect(r, 201, "txCreateExpense", saved.getValue().getId());
    }

    @Test
    void transactionCreateForeignTransfer() throws Exception {
        when(rateRepo.findEffectiveRate(USER_ID, "USD", "INR", LocalDate.of(2026, 3, 9)))
                .thenReturn(Optional.of(ExchangeRate.builder().rate(new BigDecimal("83.25000000")).build()));
        var r = send(post("/api/v1/transactions"), """
                {"type":"TRANSFER","amount":"10.0000","accountId":"a-usd","transferAccountId":"a-bank",
                 "transferAmount":"830.0000","occurredOn":"2026-03-09"}""");
        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(txRepo).save(saved.capture());
        expect(r, 201, "txCreateTransfer", saved.getValue().getId());
    }

    @Test
    void transactionErrors() throws Exception {
        expect(send(post("/api/v1/transactions"), """
                {"type":"EXPENSE","amount":"1.0000","accountId":"a-usd","occurredOn":"2026-03-09"}"""), 422, "txNoRate");
        expect(send(post("/api/v1/transactions"), """
                {"type":"EXPENSE","amount":"1.0000","accountId":"a-card","occurredOn":"2026-03-09"}"""), 409, "txArchived");
        expect(send(post("/api/v1/transactions"), """
                {"type":"TRANSFER","amount":"1.0000","accountId":"a-bank","transferAccountId":"a-bank","occurredOn":"2026-03-09"}"""),
                422, "txSameAccount");
    }

    // ── fixes ────────────────────────────────────────────────────────────

    @Test
    void badInputIs422NotA500() throws Exception {
        send(post("/api/v1/accounts"), "{\"name\":\" \",\"type\":\"BANK\",\"currency\":\"rupees\",\"openingBalance\":\"1.23456\",\"last4\":\"12\"}")
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.error.fieldErrors.name").exists()).andExpect(jsonPath("$.error.fieldErrors.currency").exists())
            .andExpect(jsonPath("$.error.fieldErrors.openingBalance").exists()).andExpect(jsonPath("$.error.fieldErrors.last4").exists());
        send(post("/api/v1/accounts"), "{}").andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.type").exists());
        send(post("/api/v1/accounts"), "{\"name\":\"x\",\"type\":\"PIGGY\",\"currency\":\"INR\"}").andExpect(status().is(422));
        send(post("/api/v1/accounts/reorder"), "{}").andExpect(status().is(422));
        send(post("/api/v1/categories"), "{\"name\":\"x\"}").andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.kind").exists());
        mvc.perform(get("/api/v1/categories?kind=SIDEWAYS").with(asUser())).andExpect(status().is(422));
        send(post("/api/v1/transactions"), "{}").andExpect(status().is(422))
            .andExpect(jsonPath("$.error.fieldErrors.amount").exists()).andExpect(jsonPath("$.error.fieldErrors.occurredOn").exists());
        send(post("/api/v1/transactions"), """
                {"type":"EXPENSE","amount":"-5","accountId":"a-bank","occurredOn":"2026-03-09"}""")
            .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.amount[0]").value("Amount must be greater than zero"));
        send(post("/api/v1/transactions"), """
                {"type":"EXPENSE","amount":"5","accountId":"a-bank","occurredOn":"09/03/2026"}""").andExpect(status().is(422));
        mvc.perform(get("/api/v1/transactions?limit=5000").with(asUser())).andExpect(status().is(422));
        mvc.perform(get("/api/v1/transactions?type=GIFT").with(asUser())).andExpect(status().is(422));
        mvc.perform(get("/api/v1/transactions?from=soon").with(asUser())).andExpect(status().is(422));
        mvc.perform(get("/api/v1/transactions?cursor=nope").with(asUser()))
           .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.cursor").exists());
        org.mockito.Mockito.verify(txRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void theCursorContinuesAfterTheLastRowOfThePreviousPage() throws Exception {
        Transaction last = tx("t2");
        last.setUser(testUser());
        when(txRepo.findById("t2")).thenReturn(Optional.of(last));
        when(txRepo.findPage(eq(USER_ID), any(), any(), any(), any(), any(), eq(last.getOccurredOn()),
                eq(last.getCreatedAt()), eq("t2"), any())).thenReturn(List.of(tx("t3")));
        mvc.perform(get("/api/v1/transactions?limit=2&cursor=t2").with(asUser()))
           .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value("t3"))
           .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

        // someone else's transaction id is not a cursor
        last.setUser(com.financetracker.api.entity.User.builder().id("other").build());
        mvc.perform(get("/api/v1/transactions?cursor=t2").with(asUser())).andExpect(status().is(422));
    }

    @Test
    void aNewDefaultAccountReplacesTheOldOne() throws Exception {
        when(accountRepo.findActiveByUserId(USER_ID)).thenReturn(List.of(bank, dollars));
        send(patch("/api/v1/accounts/a-usd"), "{\"isDefault\":true}").andExpect(jsonPath("$.data.isDefault").value(true));
        assertThat(bank.isDefault()).isFalse();
        assertThat(dollars.isDefault()).isTrue();
    }

    @Test
    void categoryRulesForTransactionsAndParents() throws Exception {
        send(post("/api/v1/transactions"), """
                {"type":"INCOME","amount":"5","accountId":"a-bank","categoryId":"c-food","occurredOn":"2026-03-09"}""")
            .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.categoryId[0]").value("Choose an income category"));
        send(post("/api/v1/transactions"), """
                {"type":"EXPENSE","amount":"5","accountId":"a-bank","categoryId":"c-gone","occurredOn":"2026-03-09"}""")
            .andExpect(status().isNotFound());
        when(txRepo.findByIdAndUserIdAndDeletedAtIsNull("t1", USER_ID)).thenReturn(Optional.of(tx("t1")));
        send(patch("/api/v1/transactions/t1"), "{\"categoryId\":\"c-gone\"}").andExpect(status().isNotFound()); // was: silently cleared
        send(post("/api/v1/categories"), "{\"name\":\"Bonus\",\"kind\":\"INCOME\",\"parentId\":\"c-food\"}")
            .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.parentId").exists());
    }

    @Test
    void transfersStayBalanced() throws Exception {
        // different currencies and no amount received: converted at the saved rate, never 10 USD → 10 INR
        when(rateRepo.findEffectiveRate(USER_ID, "USD", "INR", LocalDate.of(2026, 3, 9)))
                .thenReturn(Optional.of(ExchangeRate.builder().rate(new BigDecimal("83.25000000")).build()));
        send(post("/api/v1/transactions"), """
                {"type":"TRANSFER","amount":"10","accountId":"a-usd","transferAccountId":"a-bank","occurredOn":"2026-03-09"}""")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.transferAmount").value("832.5000"));
        // ...and with no rate either, the app is asked for it
        send(post("/api/v1/transactions"), """
                {"type":"TRANSFER","amount":"10","accountId":"a-bank","transferAccountId":"a-usd","occurredOn":"2026-03-09"}""")
            .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.transferAmount").exists());
        send(post("/api/v1/transactions"), """
                {"type":"TRANSFER","amount":"10","accountId":"a-bank","occurredOn":"2026-03-09"}""")
            .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.transferAccountId").exists());

        // editing a same-currency transfer's amount moves both sides
        Transaction transfer = tx("t9");
        transfer.setType(TransactionType.TRANSFER);
        transfer.setCategory(null);
        transfer.setTransferAccount(card);
        transfer.setTransferCurrency("INR");
        transfer.setTransferAmount(new BigDecimal("500.0000"));
        when(txRepo.findByIdAndUserIdAndDeletedAtIsNull("t9", USER_ID)).thenReturn(Optional.of(transfer));
        send(patch("/api/v1/transactions/t9"), "{\"amount\":\"650\"}")
            .andExpect(jsonPath("$.data.amount").value("650.0000")).andExpect(jsonPath("$.data.transferAmount").value("650.0000"));
    }

    @Test
    void reviewFixes() throws Exception {
        // an fxRate on a base-currency account is ignored, not multiplied in
        send(post("/api/v1/transactions"), """
                {"type":"EXPENSE","amount":"100","accountId":"a-bank","occurredOn":"2026-03-09","fxRate":"83.5"}""")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.baseAmount").value("100.0000"))
            .andExpect(jsonPath("$.data.fxRate").value("1.00000000"));
        // a converted amount too large for the column is a 422 on the amount
        when(rateRepo.findEffectiveRate(USER_ID, "USD", "INR", LocalDate.of(2026, 3, 9)))
                .thenReturn(Optional.of(ExchangeRate.builder().rate(new BigDecimal("83.00000000")).build()));
        send(post("/api/v1/transactions"), """
                {"type":"EXPENSE","amount":"20000000000000","accountId":"a-usd","occurredOn":"2026-03-09"}""")
            .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.amount").exists());

        // a cross-currency transfer: changing what was sent needs what was received
        Transaction fx = tx("t8");
        fx.setType(TransactionType.TRANSFER);
        fx.setCategory(null);
        fx.setTransferAccount(dollars);
        fx.setTransferCurrency("USD");
        fx.setTransferAmount(new BigDecimal("6.0000"));
        when(txRepo.findByIdAndUserIdAndDeletedAtIsNull("t8", USER_ID)).thenReturn(Optional.of(fx));
        send(patch("/api/v1/transactions/t8"), "{\"amount\":\"1000\"}")
            .andExpect(status().is(422)).andExpect(jsonPath("$.error.fieldErrors.transferAmount").exists());
        send(patch("/api/v1/transactions/t8"), "{\"amount\":\"1000\",\"transferAmount\":\"12\"}")
            .andExpect(jsonPath("$.data.amount").value("1000.0000")).andExpect(jsonPath("$.data.transferAmount").value("12.0000"));

        // the same Idempotency-Key with different details is refused, not replayed
        when(txRepo.findByUserIdAndIdempotencyKey(USER_ID, "k1")).thenReturn(Optional.of(tx("t1")));
        mvc.perform(post("/api/v1/transactions").with(asUser()).header("Idempotency-Key", "k1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"EXPENSE","amount":"500","accountId":"a-bank","occurredOn":"2026-03-09"}"""))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value("t1"));
        mvc.perform(post("/api/v1/transactions").with(asUser()).header("Idempotency-Key", "k1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"EXPENSE","amount":"50","accountId":"a-bank","occurredOn":"2026-03-09"}"""))
            .andExpect(status().is(409));
    }

    @Test
    void editingKeepsTheBookedRateUnlessTheDateMoves() throws Exception {
        Transaction usd = tx("t7");
        usd.setAccount(dollars);
        usd.setCurrency("USD");
        usd.setCategory(null);
        usd.setFxRate(new BigDecimal("80.00000000"));
        usd.setBaseAmount(new BigDecimal("40000.0000"));
        when(txRepo.findByIdAndUserIdAndDeletedAtIsNull("t7", USER_ID)).thenReturn(Optional.of(usd));
        send(patch("/api/v1/transactions/t7"), "{\"notes\":\"x\",\"amount\":\"100\"}")
            .andExpect(jsonPath("$.data.fxRate").value("80.00000000")).andExpect(jsonPath("$.data.baseAmount").value("8000.0000"));
        when(rateRepo.findEffectiveRate(USER_ID, "USD", "INR", LocalDate.of(2026, 4, 1)))
                .thenReturn(Optional.of(ExchangeRate.builder().rate(new BigDecimal("85.00000000")).build()));
        send(patch("/api/v1/transactions/t7"), "{\"occurredOn\":\"2026-04-01\"}")
            .andExpect(jsonPath("$.data.fxRate").value("85.00000000")).andExpect(jsonPath("$.data.baseAmount").value("8500.0000"));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private ResultActions send(MockHttpServletRequestBuilder req, String json) throws Exception {
        return mvc.perform(req.with(asUser()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static void expect(ResultActions r, int status, String key, Object... args) throws Exception {
        String expected = EXPECTED.get(key);
        assertThat(expected).as(key).isNotNull();
        r.andExpect(status().is(status)).andExpect(content().json(expected.formatted(args), JsonCompareMode.STRICT));
    }

    static final java.util.Map<String, String> EXPECTED = java.util.Map.ofEntries(
            java.util.Map.entry("txNoRate", """
                    {"ok":false,"error":{"code":"MISSING_EXCHANGE_RATE","message":"Set an exchange rate for USD → INR before recording this transaction"}}"""),
            java.util.Map.entry("txArchived", """
                    {"ok":false,"error":{"code":"ACCOUNT_ARCHIVED","message":"That account is archived"}}"""),
            java.util.Map.entry("txSameAccount", """
                    {"ok":false,"error":{"code":"SAME_ACCOUNT_TRANSFER","message":"Choose two different accounts"}}"""),
            java.util.Map.entry("accountPatch", """
                    {"ok":true,"data":{"id":"a-bank","name":"Main","type":"BANK","class":"ASSET","currency":"INR","openingBalance":"5.0000","institution":"HDFC","last4":"1234","color":"#112233","icon":"bank","isDefault":true,"sortOrder":0,"archived":false,"createdAt":"1970-01-01T00:00:00Z","balance":"-245.5000"}}"""),
            java.util.Map.entry("accountArchive", """
                    {"ok":true,"data":{"id":"a-bank","name":"Main","type":"BANK","class":"ASSET","currency":"INR","openingBalance":"5.0000","institution":"HDFC","last4":"1234","color":"#112233","icon":"bank","isDefault":true,"sortOrder":0,"archived":true,"archivedAt":"%s","createdAt":"1970-01-01T00:00:00Z","balance":"-245.5000"}}"""),
            java.util.Map.entry("accountUnarchive", """
                    {"ok":true,"data":{"id":"a-card","name":"Card","type":"CREDIT_CARD","class":"LIABILITY","currency":"INR","openingBalance":"0.0000","isDefault":false,"sortOrder":1,"archived":false,"createdAt":"1970-01-01T00:00:00Z","balance":"0.0000"}}"""),
            java.util.Map.entry("accountDelete", """
                    {"ok":true,"data":{"id":"a-card"}}"""),
            java.util.Map.entry("accountReorder", """
                    {"ok":true,"data":{"reordered":2}}"""),
            java.util.Map.entry("accountInUse", """
                    {"ok":false,"error":{"code":"ACCOUNT_IN_USE","message":"This account has transactions and cannot be deleted"}}"""),
            java.util.Map.entry("accountMissing", """
                    {"ok":false,"error":{"code":"NOT_FOUND","message":"We couldn't find that account"}}"""),
            java.util.Map.entry("txCreateTransfer", """
                    {"ok":true,"data":{"id":"%s","type":"TRANSFER","amount":"10.0000","currency":"USD","baseAmount":"832.5000","baseCurrency":"INR","fxRate":"83.25000000","accountId":"a-usd","accountName":"Dollars","transferAccountId":"a-bank","transferAccountName":"Bank","transferAmount":"830.0000","transferCurrency":"INR","occurredOn":"2026-03-09","source":"MANUAL"}}"""),
            java.util.Map.entry("accountCreate", """
                    {"ok":true,"data":{"id":"%s","name":"Wallet","type":"CREDIT_CARD","class":"LIABILITY","currency":"INR","openingBalance":"-12.5000","last4":"9876","isDefault":false,"sortOrder":0,"archived":false,"balance":"-12.5000"}}"""),
            java.util.Map.entry("txCreateExpense", """
                    {"ok":true,"data":{"id":"%s","type":"EXPENSE","amount":"120.5000","currency":"INR","baseAmount":"120.5000","baseCurrency":"INR","fxRate":"1.00000000","accountId":"a-bank","accountName":"Bank","categoryId":"c-food","categoryName":"Food","color":"#ff0000","occurredOn":"2026-03-09","merchant":"Cafe","source":"MANUAL"}}"""),
            java.util.Map.entry("txList", """
                    {"ok":true,"data":{"items":[{"id":"t1","type":"EXPENSE","amount":"500.0000","currency":"INR","baseAmount":"500.0000","baseCurrency":"INR","fxRate":"1.00000000","accountId":"a-bank","accountName":"Bank","categoryId":"c-food","categoryName":"Food","color":"#ff0000","occurredOn":"2026-03-09","merchant":"Cafe","notes":"lunch","source":"MANUAL","createdAt":"1970-01-01T00:00:00Z"},{"id":"t2","type":"TRANSFER","amount":"500.0000","currency":"INR","baseAmount":"500.0000","baseCurrency":"INR","fxRate":"1.00000000","accountId":"a-bank","accountName":"Bank","transferAccountId":"a-usd","transferAccountName":"Dollars","transferAmount":"6.0000","transferCurrency":"USD","occurredOn":"2026-03-09","source":"MANUAL","createdAt":"1970-01-01T00:00:00Z"}],"nextCursor":"t2"}}"""),
            java.util.Map.entry("txGet", """
                    {"ok":true,"data":{"id":"t1","type":"EXPENSE","amount":"500.0000","currency":"INR","baseAmount":"500.0000","baseCurrency":"INR","fxRate":"1.00000000","accountId":"a-bank","accountName":"Bank","categoryId":"c-food","categoryName":"Food","color":"#ff0000","occurredOn":"2026-03-09","merchant":"Cafe","notes":"lunch","source":"MANUAL","createdAt":"1970-01-01T00:00:00Z"}}"""),
            java.util.Map.entry("txDuplicate", """
                    {"ok":true,"data":{"id":"%s","type":"EXPENSE","amount":"500.0000","currency":"INR","baseAmount":"500.0000","baseCurrency":"INR","fxRate":"1.00000000","accountId":"a-bank","accountName":"Bank","categoryId":"c-food","categoryName":"Food","color":"#ff0000","occurredOn":"2026-04-01","merchant":"Cafe","notes":"lunch","source":"MANUAL"}}"""),
            java.util.Map.entry("txPatch", """
                    {"ok":true,"data":{"id":"t1","type":"EXPENSE","amount":"750.2500","currency":"INR","baseAmount":"750.2500","baseCurrency":"INR","fxRate":"1.00000000","accountId":"a-bank","accountName":"Bank","occurredOn":"2026-03-09","merchant":"Cafe","notes":"dinner","source":"MANUAL","createdAt":"1970-01-01T00:00:00Z"}}"""),
            java.util.Map.entry("txDelete", """
                    {"ok":true,"data":{"id":"t1"}}"""),
            java.util.Map.entry("txMissing", """
                    {"ok":false,"error":{"code":"NOT_FOUND","message":"We couldn't find that transaction"}}"""),
            java.util.Map.entry("accountsList", """
                    {"ok":true,"data":[{"id":"a-bank","name":"Bank","type":"BANK","class":"ASSET","currency":"INR","openingBalance":"1000.0000","institution":"HDFC","last4":"1234","color":"#112233","icon":"bank","isDefault":true,"sortOrder":0,"archived":false,"createdAt":"1970-01-01T00:00:00Z","balance":"749.5000"},{"id":"a-card","name":"Card","type":"CREDIT_CARD","class":"LIABILITY","currency":"INR","openingBalance":"0.0000","isDefault":false,"sortOrder":1,"archived":true,"archivedAt":"1970-01-01T00:00:00Z","createdAt":"1970-01-01T00:00:00Z","balance":"0.0000"}]}"""),
            java.util.Map.entry("categoryCreate", """
                    {"ok":true,"data":{"id":"%s","name":"Pets","kind":"EXPENSE","icon":"paw","isSystem":false,"sortOrder":0,"parentId":"c-food","archivedAt":null}}"""),
            java.util.Map.entry("categoriesAll", """
                    {"ok":true,"data":[{"id":"c-food","name":"Food","kind":"EXPENSE","color":"#ff0000","icon":"restaurant","isSystem":true,"sortOrder":0,"archivedAt":null},{"id":"c-sal","name":"Salary","kind":"INCOME","isSystem":false,"sortOrder":2,"parentId":"c-food","archivedAt":"1970-01-01T00:00:00Z"}]}"""),
            java.util.Map.entry("categoriesActive", """
                    {"ok":true,"data":[{"id":"c-food","name":"Food","kind":"EXPENSE","color":"#ff0000","icon":"restaurant","isSystem":true,"sortOrder":0,"archivedAt":null}]}"""),
            java.util.Map.entry("categoriesIncome", """
                    {"ok":true,"data":[]}"""),
            java.util.Map.entry("categoryGet", """
                    {"ok":true,"data":{"id":"c-food","name":"Food","kind":"EXPENSE","color":"#ff0000","icon":"restaurant","isSystem":true,"sortOrder":0,"archivedAt":null}}"""),
            java.util.Map.entry("categoryPatch", """
                    {"ok":true,"data":{"id":"c-food","name":"Eating out","kind":"EXPENSE","color":"#00ff00","icon":"restaurant","isSystem":true,"sortOrder":0,"archivedAt":null}}"""),
            java.util.Map.entry("categoryArchive", """
                    {"ok":true,"data":{"id":"c-food","name":"Eating out","kind":"EXPENSE","color":"#00ff00","icon":"restaurant","isSystem":true,"sortOrder":0,"archivedAt":"%s"}}"""),
            java.util.Map.entry("categoryUnarchive", """
                    {"ok":true,"data":{"id":"c-sal","name":"Salary","kind":"INCOME","isSystem":false,"sortOrder":2,"parentId":"c-food","archivedAt":null}}"""),
            java.util.Map.entry("categoryDelete", """
                    {"ok":true,"data":{"id":"c-sal"}}"""),
            java.util.Map.entry("categoryInUse", """
                    {"ok":false,"error":{"code":"CATEGORY_IN_USE","message":"This category is still used by transactions or budgets"}}""")
    );
}
