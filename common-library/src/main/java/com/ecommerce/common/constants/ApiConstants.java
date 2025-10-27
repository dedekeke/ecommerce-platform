package com.ecommerce.common.constants;

/**
 * Common API constants used across all microservices
 */
public final class ApiConstants {

    private ApiConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // API Versioning
    public static final String API_VERSION = "/api/v1";

    // Common Headers
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    public static final String HEADER_USER_ID = "X-User-ID";
    public static final String HEADER_TENANT_ID = "X-Tenant-ID";
    public static final String HEADER_REQUEST_ID = "X-Request-ID";

    // Pagination
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT_DIRECTION = "ASC";

    // Date/Time Formats
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String DATE_FORMAT = "yyyy-MM-dd";

    // Common Response Messages
    public static final String MSG_SUCCESS = "Operation completed successfully";
    public static final String MSG_CREATED = "Resource created successfully";
    public static final String MSG_UPDATED = "Resource updated successfully";
    public static final String MSG_DELETED = "Resource deleted successfully";
    public static final String MSG_NOT_FOUND = "Resource not found";
    public static final String MSG_BAD_REQUEST = "Invalid request";
    public static final String MSG_UNAUTHORIZED = "Unauthorized access";
    public static final String MSG_FORBIDDEN = "Access forbidden";
    public static final String MSG_INTERNAL_ERROR = "Internal server error";

    // Error Codes
    public static final String ERR_VALIDATION = "VALIDATION_ERROR";
    public static final String ERR_NOT_FOUND = "NOT_FOUND";
    public static final String ERR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERR_FORBIDDEN = "FORBIDDEN";
    public static final String ERR_BUSINESS = "BUSINESS_ERROR";
    public static final String ERR_INTERNAL = "INTERNAL_ERROR";
}
