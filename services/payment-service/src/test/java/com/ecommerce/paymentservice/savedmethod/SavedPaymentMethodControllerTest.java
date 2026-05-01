package com.ecommerce.paymentservice.savedmethod;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct method-level tests for {@link SavedPaymentMethodController}. Avoids
 * @WebMvcTest because the payment-service Spring context is heavy
 * (Eureka, Kafka, gRPC) and a method-level test exercises the same auth +
 * routing logic without the boot overhead.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SavedPaymentMethodController")
class SavedPaymentMethodControllerTest {

    @Mock
    private SavedPaymentMethodService service;

    @InjectMocks
    private SavedPaymentMethodController controller;

    private Jwt userJwt;

    @BeforeEach
    void setUp() {
        userJwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("scope", "read write")
            .issuedAt(Instant.parse("2026-04-29T10:00:00Z"))
            .expiresAt(Instant.parse("2026-04-29T11:00:00Z"))
            .build();
    }

    @Test
    @DisplayName("attach_should_return201_andDelegateToService")
    void attach_should_return201_andDelegateToService() {
        SavedPaymentMethod saved = SavedPaymentMethod.builder()
            .id(1L)
            .userId("user-1")
            .provider("STRIPE")
            .providerId("pm_1")
            .build();
        when(service.attach("user-1", "tok_test")).thenReturn(saved);

        ResponseEntity<SavedPaymentMethod> response = controller.attach(
            new SavedPaymentMethodDtos.AttachRequest("tok_test"), userJwt, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(saved);
    }

    @Test
    @DisplayName("attach_should_useHeaderUserId_when_jwtMissing")
    void attach_should_useHeaderUserId_when_jwtMissing() {
        SavedPaymentMethod saved = SavedPaymentMethod.builder().id(1L).userId("dev-user").build();
        when(service.attach("dev-user", "tok")).thenReturn(saved);

        ResponseEntity<SavedPaymentMethod> response = controller.attach(
            new SavedPaymentMethodDtos.AttachRequest("tok"), null, "dev-user");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("attach_should_throwSecurity_when_noAuthSource")
    void attach_should_throwSecurity_when_noAuthSource() {
        assertThatThrownBy(() -> controller.attach(
            new SavedPaymentMethodDtos.AttachRequest("tok"), null, null))
            .isInstanceOf(SecurityException.class);
        verify(service, never()).attach(any(), any());
    }

    @Test
    @DisplayName("list_should_returnUsersOwnMethods")
    void list_should_returnUsersOwnMethods() {
        SavedPaymentMethod m = SavedPaymentMethod.builder().id(1L).userId("user-1").build();
        when(service.listForUser("user-1")).thenReturn(List.of(m));

        ResponseEntity<List<SavedPaymentMethod>> response = controller.list("user-1", userJwt, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(m);
    }

    @Test
    @DisplayName("list_should_throwSecurity_when_userIdMismatch")
    void list_should_throwSecurity_when_userIdMismatch() {
        assertThatThrownBy(() -> controller.list("other-user", userJwt, null))
            .isInstanceOf(SecurityException.class);
        verify(service, never()).listForUser(any());
    }

    @Test
    @DisplayName("setDefault_should_returnUpdatedMethod")
    void setDefault_should_returnUpdatedMethod() {
        SavedPaymentMethod m = SavedPaymentMethod.builder()
            .id(2L).userId("user-1").isDefault(true).build();
        when(service.setDefault("user-1", 2L)).thenReturn(m);

        ResponseEntity<SavedPaymentMethod> response = controller.setDefault(2L, userJwt, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("delete_should_return204")
    void delete_should_return204() {
        ResponseEntity<Void> response = controller.delete(2L, userJwt, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete("user-1", 2L);
    }

    @Test
    @DisplayName("jwtWithBlankSubject_should_fallbackToHeader")
    void jwtWithBlankSubject_should_fallbackToHeader() {
        Jwt blankSub = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claims(claims -> claims.put("sub", ""))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("extra", Map.of())
            .build();
        SavedPaymentMethod saved = SavedPaymentMethod.builder().id(1L).userId("dev-user").build();
        when(service.attach("dev-user", "tok")).thenReturn(saved);

        ResponseEntity<SavedPaymentMethod> response = controller.attach(
            new SavedPaymentMethodDtos.AttachRequest("tok"), blankSub, "dev-user");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
