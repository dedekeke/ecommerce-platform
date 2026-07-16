package com.ecommerce.orderservice.security;

import com.ecommerce.orderservice.exception.UserMismatchException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserIdentityResolverTest {

    private static final String SUBJECT = "auth0|user-a";
    private static final String OTHER = "auth0|user-b";

    private final UserIdentityResolver resolver = new UserIdentityResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
    }

    private static void authenticateAs(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("principal", "creds", authorities));
    }

    // ---- resolveUserId ------------------------------------------------------

    @Test
    void should_returnClientUserId_when_noJwt() {
        assertThat(resolver.resolveUserId(OTHER, null)).isEqualTo(OTHER);
    }

    @Test
    void should_returnSubject_when_clientUserIdMatches() {
        authenticateAs("SCOPE_read");
        assertThat(resolver.resolveUserId(SUBJECT, jwtWithSubject(SUBJECT))).isEqualTo(SUBJECT);
    }

    @Test
    void should_returnSubject_when_clientUserIdBlank() {
        authenticateAs("SCOPE_read");
        assertThat(resolver.resolveUserId("  ", jwtWithSubject(SUBJECT))).isEqualTo(SUBJECT);
    }

    @Test
    void should_throw_when_clientUserIdDiffers_andNotAdmin() {
        authenticateAs("SCOPE_read");
        assertThatThrownBy(() -> resolver.resolveUserId(OTHER, jwtWithSubject(SUBJECT)))
                .isInstanceOf(UserMismatchException.class);
    }

    @Test
    void should_honourClientUserId_when_admin() {
        authenticateAs("SCOPE_admin");
        assertThat(resolver.resolveUserId(OTHER, jwtWithSubject(SUBJECT))).isEqualTo(OTHER);
    }

    @Test
    void should_returnSubject_when_admin_andClientUserIdBlank() {
        authenticateAs("SCOPE_admin");
        assertThat(resolver.resolveUserId(null, jwtWithSubject(SUBJECT))).isEqualTo(SUBJECT);
    }

    // ---- assertCanActFor ----------------------------------------------------

    @Test
    void should_notThrow_when_noJwt() {
        assertThatCode(() -> resolver.assertCanActFor(OTHER, null)).doesNotThrowAnyException();
    }

    @Test
    void should_notThrow_when_ownerMatchesSubject() {
        authenticateAs("SCOPE_read");
        assertThatCode(() -> resolver.assertCanActFor(SUBJECT, jwtWithSubject(SUBJECT)))
                .doesNotThrowAnyException();
    }

    @Test
    void should_throw_when_ownerDiffers_andNotAdmin() {
        authenticateAs("SCOPE_read");
        assertThatThrownBy(() -> resolver.assertCanActFor(OTHER, jwtWithSubject(SUBJECT)))
                .isInstanceOf(UserMismatchException.class);
    }

    @Test
    void should_notThrow_when_ownerDiffers_andAdmin() {
        authenticateAs("SCOPE_admin");
        assertThatCode(() -> resolver.assertCanActFor(OTHER, jwtWithSubject(SUBJECT)))
                .doesNotThrowAnyException();
    }

    // ---- canAccess (enumeration-safe, non-throwing) -------------------------

    @Test
    void should_allowAccess_when_noJwt() {
        assertThat(resolver.canAccess(OTHER, null)).isTrue();
    }

    @Test
    void should_allowAccess_when_ownerMatchesSubject() {
        authenticateAs("SCOPE_read");
        assertThat(resolver.canAccess(SUBJECT, jwtWithSubject(SUBJECT))).isTrue();
    }

    @Test
    void should_denyAccess_when_ownerDiffers_andNotAdmin() {
        authenticateAs("SCOPE_read");
        assertThat(resolver.canAccess(OTHER, jwtWithSubject(SUBJECT))).isFalse();
    }

    @Test
    void should_allowAccess_when_ownerDiffers_andAdmin() {
        authenticateAs("SCOPE_admin");
        assertThat(resolver.canAccess(OTHER, jwtWithSubject(SUBJECT))).isTrue();
    }
}
