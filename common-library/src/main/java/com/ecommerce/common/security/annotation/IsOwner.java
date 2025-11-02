package com.ecommerce.common.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Annotation to restrict access to resource owners or admins.
 * This should be used with a custom security evaluator.
 *
 * Example:
 * <pre>
 * {@code
 * @IsOwner
 * public Order getOrder(@PathVariable Long orderId) { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("@ownershipEvaluator.isOwnerOrAdmin(authentication, #id) or hasAuthority('ROLE_ADMIN')")
public @interface IsOwner {
    /**
     * The parameter name that contains the resource ID to check ownership.
     */
    String value() default "id";
}
