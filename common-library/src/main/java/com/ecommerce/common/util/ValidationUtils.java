package com.ecommerce.common.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Utility class for common validation operations
 */
public final class ValidationUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[1-9]\\d{1,14}$" // E.164 format
    );

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Check if a string is null or empty
     */
    public static boolean isEmpty(String str) {
        return StringUtils.isEmpty(str);
    }

    /**
     * Check if a string is not null and not empty
     */
    public static boolean isNotEmpty(String str) {
        return StringUtils.isNotEmpty(str);
    }

    /**
     * Check if a string is null, empty, or contains only whitespace
     */
    public static boolean isBlank(String str) {
        return StringUtils.isBlank(str);
    }

    /**
     * Check if a string is not null, not empty, and contains non-whitespace
     */
    public static boolean isNotBlank(String str) {
        return StringUtils.isNotBlank(str);
    }

    /**
     * Validate email format
     */
    public static boolean isValidEmail(String email) {
        if (isEmpty(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validate phone number format (E.164)
     */
    public static boolean isValidPhone(String phone) {
        if (isEmpty(phone)) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Check if a string is alphanumeric
     */
    public static boolean isAlphanumeric(String str) {
        if (isEmpty(str)) {
            return false;
        }
        return ALPHANUMERIC_PATTERN.matcher(str).matches();
    }

    /**
     * Check if a collection is null or empty
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Check if a collection is not null and not empty
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }

    /**
     * Require non-null object
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    /**
     * Require non-blank string
     */
    public static String requireNonBlank(String str, String message) {
        if (isBlank(str)) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * Validate that a number is positive
     */
    public static void requirePositive(Number number, String message) {
        if (number.doubleValue() <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Validate that a number is non-negative
     */
    public static void requireNonNegative(Number number, String message) {
        if (number.doubleValue() < 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
