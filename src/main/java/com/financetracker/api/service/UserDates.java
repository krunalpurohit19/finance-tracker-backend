package com.financetracker.api.service;

import com.financetracker.api.entity.UserSettings;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Calendar dates as the user sees them: "today" in their timezone, not the server's. */
final class UserDates {

    private UserDates() {}

    static LocalDate today(Clock clock, UserSettings settings) {
        return LocalDate.now(clock.withZone(zone(settings)));
    }

    /** The user's zone; UTC if a legacy row holds something the tz database doesn't know. */
    static ZoneId zone(UserSettings settings) {
        try {
            return ZoneId.of(settings.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return ZoneOffset.UTC;
        }
    }
}
