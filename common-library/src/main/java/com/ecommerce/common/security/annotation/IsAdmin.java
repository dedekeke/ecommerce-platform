package com.ecommerce.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to restrict access to admin users only.
 * Checks for admin role, scope, or permission.
 *
 * Example:
 * <pre>
 * {@code
 * @IsAdmin
 * public void createProduct(Product product) { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'SCOPE_admin', 'admin:access')")
public @interface IsAdmin {
}
