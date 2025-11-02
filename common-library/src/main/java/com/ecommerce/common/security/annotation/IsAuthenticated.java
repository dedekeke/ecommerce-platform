package com.ecommerce.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to require authentication for accessing a method or class.
 * This is a convenience annotation that makes the code more readable.
 *
 * Example:
 * <pre>
 * {@code
 * @IsAuthenticated
 * public UserProfile getMyProfile() { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("isAuthenticated()")
public @interface IsAuthenticated {
}
