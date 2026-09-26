package com.financetracker.api.service;

import com.financetracker.api.dto.budget.BudgetDtos.BudgetStatus;
import com.financetracker.api.dto.budget.BudgetDtos.CreateBudgetRequest;
import com.financetracker.api.entity.Budget;
import com.financetracker.api.entity.Category;
import com.financetracker.api.entity.enums.CategoryKind;
import com.financetracker.api.exception.ApiException;
import com.financetracker.api.repository.BudgetRepository;
import com.financetracker.api.repository.CategoryRepository;
import com.financetracker.api.repository.TransactionRepository;
import com.financetracker.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    static final String USER = "u1";

    @Mock BudgetRepository budgetRepo;
    @Mock TransactionRepository txRepo;
    @Mock CategoryRepository categoryRepo;
    @Mock UserRepository userRepo;
    @InjectMocks BudgetService service;

    // ── status thresholds: NEAR_LIMIT from 85%, EXCEEDED from 100% ──

    @ParameterizedTest
    @CsvSource({
            "1000, 0,       0.00,   OK",
            "1000, 849.99,  85.00,  NEAR_LIMIT",   // rounds up to the threshold
            "1000, 849.94,  84.99,  OK",
            "1000, 848.45,  84.85,  OK",           // a tie: HALF_UP, as the old code rounded
            "1000, 850,     85.00,  NEAR_LIMIT",
            "1000, 999.99,  100.00, EXCEEDED",     // HALF_UP to 100.00
            "1000, 999.94,  99.99,  NEAR_LIMIT",
            "1000, 1500,    150.00, EXCEEDED",
            "3,    1,       33.33,  OK",
    })
    void percentAndStatus(String amount, String spent, String percent, BudgetStatus status) {
        Budget b = Budget.builder().id("b").amount(new BigDecimal(amount)).effectiveFrom(LocalDate.EPOCH).build();
        var p = BudgetService.progress(b, new BigDecimal(spent), YearMonth.of(2026, 9));
        assertThat(p.percentUsed()).isEqualByComparingTo(percent);
        assertThat(p.status()).isEqualTo(status);
        assertThat(p.remaining()).isEqualByComparingTo(new BigDecimal(amount).subtract(new BigDecimal(spent)));
    }

    @Test
    void zeroAmountBudgetHasNoPercentage() {
        Budget b = Budget.builder().id("b").amount(BigDecimal.ZERO).effectiveFrom(LocalDate.EPOCH).build();
        var p = BudgetService.progress(b, BigDecimal.TEN, YearMonth.of(2026, 9));
        assertThat(p.percentUsed()).isNull();
        assertThat(p.status()).isEqualTo(BudgetStatus.OK);
    }

    @Test
    void noBudgetsMeansNoSpendQuery() {
        assertThat(service.list(USER, YearMonth.of(2026, 9))).isEmpty();
        verifyNoInteractions(txRepo);
    }

    // ── create: validation happens before the overlap query ──

    @Test
    void unknownCategoryFailsBeforeCheckingOverlap() {
        assertThatThrownBy(() -> service.create(USER, new CreateBudgetRequest("nope", BigDecimal.ONE, null, null)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "NOT_FOUND");
        verify(budgetRepo, never()).countOverlapping(any(), any(), any(), any(), any());
        verify(budgetRepo, never()).save(any());
    }

    @Test
    void overallBudgetDefaultsToThisMonthAndIsOpenEnded() {
        when(budgetRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var created = service.create(USER, new CreateBudgetRequest(null, new BigDecimal("2000"), null, null));

        assertThat(created.effectiveFrom()).isEqualTo(YearMonth.now().atDay(1));
        assertThat(created.categoryId()).isNull();
        assertThat(created.amount()).hasToString("2000.0000");
        verify(budgetRepo).countOverlapping(USER, null, "", YearMonth.now().atDay(1), LocalDate.of(9999, 12, 31));
        verifyNoInteractions(categoryRepo);
    }

    @Test
    void categoryMustBeAnExpenseCategory() {
        when(categoryRepo.findByIdAndUserIdAndDeletedAtIsNull("c", USER))
                .thenReturn(java.util.Optional.of(Category.builder().id("c").kind(CategoryKind.INCOME).build()));
        assertThatThrownBy(() -> service.create(USER, new CreateBudgetRequest("c", BigDecimal.ONE, null, null)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
        verify(budgetRepo, never()).save(any());
    }
}
