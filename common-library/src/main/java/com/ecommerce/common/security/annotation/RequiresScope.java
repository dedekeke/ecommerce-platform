package com.ecommerce.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to require a specific OAuth2 scope for accessing a method or class.
 * This is a convenience annotation that wraps @PreAuthorize for scope checking.
 *
 * Example:
 * <pre>
 * {@code
 * @RequiresScope("read:products")
 * public List<Product> getProducts() { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("hasAuthority('SCOPE_' + @requiresScopeHandler.extractScope(#root))")
public @interface RequiresScope {
    /**
     * The OAuth2 scope required to access the annotated method/class.
     */
    String value();
}
