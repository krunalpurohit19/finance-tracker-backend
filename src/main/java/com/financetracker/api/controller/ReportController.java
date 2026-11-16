package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.report.ReportDtos.AnalyticsResponse;
import com.financetracker.api.dto.report.ReportDtos.DashboardResponse;
import com.financetracker.api.dto.report.ReportDtos.NetWorthResponse;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.ReportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** The read-only reports: /dashboard, /analytics and /net-worth. */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /**
     * @param preset the period; the app sends {@code preset}, older builds of the API read {@code period},
     *               so both are accepted
     */
    @GetMapping("/dashboard")
    public ApiEnvelope.Success<DashboardResponse> dashboard(
            @RequestParam(required = false) String preset, @RequestParam(required = false) String period,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return new ApiEnvelope.Success<>(
                reports.getDashboard(SecurityUtils.currentUserId(), preset(preset, period), from, to));
    }

    @GetMapping("/analytics")
    public ApiEnvelope.Success<AnalyticsResponse> analytics(
            @RequestParam(required = false) String preset, @RequestParam(required = false) String period,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return new ApiEnvelope.Success<>(
                reports.getAnalytics(SecurityUtils.currentUserId(), preset(preset, period), from, to));
    }

    @GetMapping("/net-worth")
    public ApiEnvelope.Success<NetWorthResponse> netWorth(
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Between 1 and 60 months")
            @Max(value = 60, message = "Between 1 and 60 months") int months) {
        return new ApiEnvelope.Success<>(reports.getNetWorth(SecurityUtils.currentUserId(), months));
    }

    private static String preset(String preset, String period) {
        return preset != null ? preset : period != null ? period : "this-month";
    }
}
