package com.financetracker.api.service;

import com.financetracker.api.entity.SavingsGoal;
import com.financetracker.api.entity.enums.GoalStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Goal progress and deadline maths, relative to a fixed "today". */
class SavingsServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    static SavingsGoal goal(String target, LocalDate targetDate) {
        return SavingsGoal.builder().id("g").name("g").targetAmount(new BigDecimal(target)).targetDate(targetDate)
                .status(GoalStatus.ACTIVE).build();
    }

    @Test
    void deadlineMonthsCountCalendarMonthsAndRoundTheMonthlyAmountUp() {
        var p = SavingsService.progress(goal("1000.0000", LocalDate.of(2027, 1, 15)), new BigDecimal("0.0000"), TODAY);
        assertThat(p.monthsRemaining()).isEqualTo(4);                 // Sep → Jan
        assertThat(p.requiredMonthly()).isEqualByComparingTo("250.00");
        assertThat(p.overdue()).isFalse();
    }

    @Test
    void aDeadlineThisMonthStillLeavesOneMonth() {
        var p = SavingsService.progress(goal("1000.0000", LocalDate.of(2026, 9, 30)), new BigDecimal("400.0000"), TODAY);
        assertThat(p.monthsRemaining()).isEqualTo(1);
        assertThat(p.requiredMonthly()).isEqualByComparingTo("600.00");
    }

    @Test
    void overdueGoalsNeedTheWholeRemainder() {
        var p = SavingsService.progress(goal("1000.0000", LocalDate.of(2026, 9, 26)), new BigDecimal("400.0000"), TODAY);
        assertThat(p.overdue()).isTrue();
        assertThat(p.monthsRemaining()).isNull();
        assertThat(p.requiredMonthly()).isEqualByComparingTo("600.0000");
    }

    @Test
    void achievedGoalsHaveNoDeadlinePressureAndNoNegativeRemainder() {
        var p = SavingsService.progress(goal("500.0000", LocalDate.of(2026, 1, 1)), new BigDecimal("600.0000"), TODAY);
        assertThat(p.achieved()).isTrue();
        assertThat(p.overdue()).isFalse();
        assertThat(p.requiredMonthly()).isNull();
        assertThat(p.remaining()).isEqualByComparingTo("0");
        assertThat(p.progress()).isEqualByComparingTo("120.00");
    }

    @Test
    void progressRoundsHalfUp() {
        var p = SavingsService.progress(goal("1000.0000", null), new BigDecimal("848.45"), TODAY);
        assertThat(p.progress()).isEqualByComparingTo("84.85");
    }

    @Test
    void timingIsRelativeToTheGivenDayNotTheClock() {
        LocalDate day = LocalDate.of(2031, 3, 10);
        assertThat(SavingsService.progress(goal("1000.0000", day.minusDays(1)), new BigDecimal("0.0000"), day).overdue()).isTrue();
        var onDeadline = SavingsService.progress(goal("1000.0000", day), new BigDecimal("0.0000"), day);
        assertThat(onDeadline.overdue()).isFalse(); // the deadline day itself is not overdue
        assertThat(onDeadline.monthsRemaining()).isEqualTo(1);
        assertThat(SavingsService.progress(goal("1000.0000", LocalDate.of(2031, 7, 1)), new BigDecimal("0.0000"), day)
                .monthsRemaining()).isEqualTo(4);
    }

    @Test
    void requiredMonthlyRoundsHalfUpToCents() {
        assertThat(SavingsService.progress(goal("1000.0000", LocalDate.of(2026, 12, 1)), new BigDecimal("0.0000"), TODAY)
                .requiredMonthly()).isEqualTo(new BigDecimal("333.33"));
        assertThat(SavingsService.progress(goal("1000.0100", LocalDate.of(2026, 11, 1)), new BigDecimal("0.0000"), TODAY)
                .requiredMonthly()).isEqualTo(new BigDecimal("500.01")); // 500.005 → HALF_UP
    }

    @Test
    void savingExactlyTheTargetIsAchieved() {
        var p = SavingsService.progress(goal("1000.0000", LocalDate.of(2026, 1, 1)), new BigDecimal("1000.0000"), TODAY);
        assertThat(p.achieved()).isTrue();
        assertThat(p.overdue()).isFalse();
        assertThat(p.requiredMonthly()).isNull();
    }
}
