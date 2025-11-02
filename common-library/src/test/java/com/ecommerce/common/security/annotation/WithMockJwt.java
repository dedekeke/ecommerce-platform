package com.ecommerce.common.security.annotation;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.*;

/**
 * Custom annotation for testing with mock JWT authentication.
 * Can be used on test methods or classes to simulate authenticated users.
 *
 * Example:
 * <pre>
 * {@code
 * @Test
 * @WithMockJwt(subject = "auth0|test-user", scopes = {"read:products", "write:products"})
 * public void testWithAuthentication() { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = WithMockJwtSecurityContextFactory.class)
public @interface WithMockJwt {

    /**
     * The user ID (subject claim).
     */
    String subject() default "auth0|test-user-123";

    /**
     * OAuth2 scopes to include in the token.
     */
    String[] scopes() default {};

    /**
     * Permissions to include in the token.
     */
    String[] permissions() default {};

    /**
     * User's email address.
     */
    String email() default "";

    /**
     * User's full name.
     */
    String name() default "";

    /**
     * Whether this user is an admin.
     */
    boolean admin() default false;
}
