package com.financetracker.api.service;

import com.financetracker.api.entity.UserSettings;
import com.financetracker.api.repository.UserSettingsRepository;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Calendar dates as the user sees them: "today" in their timezone, not the server's. */
final class UserDates {

    /** The earliest and latest dates the API accepts (CalendarDateDeserializer): the bounds for "all time". */
    static final LocalDate MIN_DATE = LocalDate.of(1000, 1, 1);
    static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private UserDates() {}

    static LocalDate today(Clock clock, UserSettings settings) {
        return LocalDate.now(clock.withZone(zone(settings)));
    }

    /** Today for {@code userId}; UTC if they have no settings row. */
    static LocalDate today(Clock clock, UserSettingsRepository settingsRepo, String userId) {
        return settingsRepo.findById(userId).map(s -> today(clock, s)).orElseGet(() -> LocalDate.now(clock));
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
