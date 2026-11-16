package com.financetracker.api.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Every LocalDate in a request body: an ISO calendar date "YYYY-MM-DD" in the years a MySQL DATE
 * holds (1000-9999). Plain ISO parsing accepts "+10000-01-01" (a 500 at the database) and
 * "-0005-03-03" (silently stored as a different date); both become a 422 field error instead.
 */
@JsonComponent
public class CalendarDateDeserializer extends JsonDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDate) ctx.handleUnexpectedToken(LocalDate.class, p);
        }
        String text = p.getText().strip();
        try {
            LocalDate date = LocalDate.parse(text);
            if (date.getYear() >= 1000 && date.getYear() <= 9999) return date;
        } catch (DateTimeParseException ignored) {
            // reported below
        }
        return (LocalDate) ctx.handleWeirdStringValue(LocalDate.class, text, "not a calendar date YYYY-MM-DD in 1000-9999");
    }

    @Override
    public Class<?> handledType() {
        return LocalDate.class;
    }
}
