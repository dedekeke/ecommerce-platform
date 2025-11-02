package com.ecommerce.common.security.annotation;

import com.ecommerce.common.security.util.JwtTestHelper;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.*;

/**
 * Factory for creating security context from @WithMockJwt annotation.
 */
public class WithMockJwtSecurityContextFactory implements WithSecurityContextFactory<WithMockJwt> {

    @Override
    public SecurityContext createSecurityContext(WithMockJwt annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        // Build JWT based on annotation attributes
        Jwt jwt;
        if (annotation.admin()) {
            jwt = JwtTestHelper.createAdminJwt();
        } else {
            Map<String, Object> claims = new HashMap<>();

            // Add scopes
            if (annotation.scopes().length > 0) {
                claims.put("scope", String.join(" ", annotation.scopes()));
            }

            // Add permissions
            if (annotation.permissions().length > 0) {
                claims.put("permissions", Arrays.asList(annotation.permissions()));
            }

            // Add email
            if (!annotation.email().isEmpty()) {
                claims.put("email", annotation.email());
                claims.put("email_verified", true);
            }

            // Add name
            if (!annotation.name().isEmpty()) {
                claims.put("name", annotation.name());
            }

            List<String> permissions = annotation.permissions().length > 0 ?
                    Arrays.asList(annotation.permissions()) :
                    Collections.emptyList();

            jwt = JwtTestHelper.createJwt(annotation.subject(), permissions, claims);
        }

        JwtAuthenticationToken authentication = JwtTestHelper.createAuthenticationToken(jwt);
        context.setAuthentication(authentication);

        return context;
    }
}
