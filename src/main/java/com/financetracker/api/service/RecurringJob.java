package com.financetracker.api.service;

import com.financetracker.api.repository.RecurringTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Posts due recurring transactions without the app being opened. Hourly rather than nightly: each
 * user's "today" is in their own timezone, so every hour is midnight for someone. Safe to run on
 * several instances at once: generation locks the rules and is idempotent.
 */
@Component
public class RecurringJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringJob.class);

    private final RecurringTransactionRepository recurringRepo;
    private final RecurringService recurringService;
    private final Clock clock;

    public RecurringJob(RecurringTransactionRepository recurringRepo, RecurringService recurringService, Clock clock) {
        this.recurringRepo = recurringRepo;
        this.recurringService = recurringService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.recurring.cron:0 5 * * * *}")
    public void run() {
        // +1 day covers users east of the server; generate() then applies each user's real today.
        int created = 0;
        for (String userId : recurringRepo.findUserIdsWithDueRules(LocalDate.now(clock).plusDays(1))) {
            try {
                created += recurringService.generate(userId).created();
            } catch (RuntimeException e) {
                log.error("Recurring generation failed for user {}", userId, e); // one user must not stop the rest
            }
        }
        if (created > 0) log.info("Recurring job posted {} transactions", created);
    }
}
