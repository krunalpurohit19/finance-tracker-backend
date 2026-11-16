package com.financetracker.api.controller;

import com.financetracker.api.dto.ApiEnvelope;
import com.financetracker.api.dto.report.ReportDtos.AnalyticsResponse;
import com.financetracker.api.dto.report.ReportDtos.DashboardResponse;
import com.financetracker.api.dto.report.ReportDtos.NetWorthResponse;
import com.financetracker.api.security.SecurityUtils;
import com.financetracker.api.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The read-only reports: /dashboard, /analytics and /net-worth. */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/dashboard")
    public ApiEnvelope.Success<DashboardResponse> dashboard(
            @RequestParam(defaultValue = "this-month") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return new ApiEnvelope.Success<>(reports.getDashboard(SecurityUtils.currentUserId(), period, from, to));
    }

    @GetMapping("/analytics")
    public ApiEnvelope.Success<AnalyticsResponse> analytics(
            @RequestParam(defaultValue = "this-month") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return new ApiEnvelope.Success<>(reports.getAnalytics(SecurityUtils.currentUserId(), period, from, to));
    }

    @GetMapping("/net-worth")
    public ApiEnvelope.Success<NetWorthResponse> netWorth(@RequestParam(defaultValue = "12") int months) {
        return new ApiEnvelope.Success<>(reports.getNetWorth(SecurityUtils.currentUserId(), months));
    }
}
