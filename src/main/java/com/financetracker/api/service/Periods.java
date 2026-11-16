package com.financetracker.api.service;

import com.financetracker.api.dto.report.ReportDtos.DateRange;
import com.financetracker.api.dto.report.ReportDtos.Period;
import com.financetracker.api.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Report periods: a preset ("this-month", ...) resolved to inclusive dates relative to the user's today. */
final class Periods {

    private Periods() {}

    static Period resolve(String preset, LocalDate from, LocalDate to, LocalDate today) {
        LocalDate[] range = switch (preset) {
            case "this-month" -> new LocalDate[]{today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth())};
            case "last-month" -> {
                YearMonth prev = YearMonth.from(today).minusMonths(1);
                yield new LocalDate[]{prev.atDay(1), prev.atEndOfMonth()};
            }
            case "last-7-days" -> new LocalDate[]{today.minusDays(6), today};
            case "last-30-days" -> new LocalDate[]{today.minusDays(29), today};
            case "this-year" -> new LocalDate[]{today.withDayOfYear(1), LocalDate.of(today.getYear(), 12, 31)};
            case "last-year" -> new LocalDate[]{LocalDate.of(today.getYear() - 1, 1, 1), LocalDate.of(today.getYear() - 1, 12, 31)};
            case "custom" -> {
                if (from == null || to == null) throw invalid("from", "A custom period needs both a start and an end date");
                if (from.isAfter(to)) throw invalid("to", "Start date must not be after end date");
                yield new LocalDate[]{from, to};
            }
            default -> throw invalid("preset", "Unknown period");
        };
        return new Period(preset, range[0], range[1]);
    }

    private static ApiException invalid(String field, String message) {
        return ApiException.validationFailed(message, Map.of(field, List.of(message)));
    }

    /** The period a report compares against: the month(s) or year before, else the same number of days before. */
    static DateRange previous(Period p) {
        return switch (p.preset()) {
            case "this-month", "last-month" -> {
                LocalDate to = p.to().minusMonths(1);
                yield new DateRange(p.from().minusMonths(1), to.withDayOfMonth(to.lengthOfMonth()));
            }
            case "this-year", "last-year" -> new DateRange(p.from().minusYears(1), p.to().minusYears(1));
            default -> {
                long days = ReportService.daysInclusive(p);
                yield new DateRange(p.from().minusDays(days), p.to().minusDays(days));
            }
        };
    }
}
