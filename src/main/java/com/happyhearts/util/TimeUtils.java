package com.happyhearts.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class TimeUtils {

    private TimeUtils() {
    }

    public static ZoneId kigali() {
        return ZoneId.of("Africa/Kigali");
    }

    public static LocalDate toKigaliDate(Instant instant) {
        return instant.atZone(kigali()).toLocalDate();
    }

    public static LocalTime toKigaliTime(Instant instant) {
        return instant.atZone(kigali()).toLocalTime();
    }

    public static Instant startOfDayKigali(LocalDate date) {
        return date.atStartOfDay(kigali()).toInstant();
    }

    public static Instant endOfDayKigali(LocalDate date) {
        return date.plusDays(1).atStartOfDay(kigali()).toInstant();
    }

    public static ZonedDateTime toKigaliZoned(Instant instant) {
        return instant.atZone(kigali());
    }
}
