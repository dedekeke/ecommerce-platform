package com.ecommerce.common.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Utility class for common date and time operations
 */
public final class DateUtils {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_DATE;

    private DateUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Get current UTC time
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Get current UTC instant
     */
    public static Instant nowInstant() {
        return Instant.now();
    }

    /**
     * Convert LocalDateTime to epoch milliseconds
     */
    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * Convert epoch milliseconds to LocalDateTime
     */
    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC);
    }

    /**
     * Format LocalDateTime to ISO 8601 string
     */
    public static String formatIso(LocalDateTime dateTime) {
        return dateTime.format(ISO_DATE_TIME);
    }

    /**
     * Parse ISO 8601 string to LocalDateTime
     */
    public static LocalDateTime parseIso(String dateTimeString) {
        return LocalDateTime.parse(dateTimeString, ISO_DATE_TIME);
    }

    /**
     * Check if a date is in the future
     */
    public static boolean isFuture(LocalDateTime dateTime) {
        return dateTime.isAfter(nowUtc());
    }

    /**
     * Check if a date is in the past
     */
    public static boolean isPast(LocalDateTime dateTime) {
        return dateTime.isBefore(nowUtc());
    }

    /**
     * Add days to current UTC time
     */
    public static LocalDateTime addDays(int days) {
        return nowUtc().plusDays(days);
    }

    /**
     * Add hours to current UTC time
     */
    public static LocalDateTime addHours(int hours) {
        return nowUtc().plusHours(hours);
    }

    /**
     * Add minutes to current UTC time
     */
    public static LocalDateTime addMinutes(int minutes) {
        return nowUtc().plusMinutes(minutes);
    }

    /**
     * Convert java.util.Date to LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * Convert LocalDateTime to java.util.Date
     */
    public static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
