package com.ecommerce.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to require a specific permission for accessing a method or class.
 * This is useful for Auth0 RBAC (Role-Based Access Control) permissions.
 *
 * Example:
 * <pre>
 * {@code
 * @RequiresPermission("delete:products")
 * public void deleteProduct(Long id) { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("hasAuthority(#permission)")
public @interface RequiresPermission {
    /**
     * The permission required to access the annotated method/class.
     */
    String value() default "";

    /**
     * SpEL expression reference for the permission.
     * Use this when permission needs to be determined dynamically.
     */
    String permission() default "";
}
