package com.ecommerce.paymentservice.savedmethod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SavedPaymentMethodService} (§3.9).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SavedPaymentMethodService")
class SavedPaymentMethodServiceTest {

    @Mock
    private SavedPaymentMethodRepository repository;

    @Mock
    private PaymentProviderAdapter providerAdapter;

    @InjectMocks
    private SavedPaymentMethodService service;

    @Test
    @DisplayName("attach_should_persistRow_andDelegateToAdapter")
    void attach_should_persistRow_andDelegateToAdapter() {
        when(providerAdapter.providerName()).thenReturn("STRIPE");
        when(providerAdapter.attachPaymentMethod("user-1", "tok"))
            .thenReturn(new PaymentProviderAdapter.AttachResult("pm_123", "4242", "VISA", 12, 2030));
        when(repository.findByUserId("user-1")).thenReturn(List.of());
        when(repository.save(any(SavedPaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedPaymentMethod saved = service.attach("user-1", "tok");

        assertThat(saved.getProvider()).isEqualTo("STRIPE");
        assertThat(saved.getProviderId()).isEqualTo("pm_123");
        assertThat(saved.getLast4()).isEqualTo("4242");
        assertThat(saved.getBrand()).isEqualTo("VISA");
        assertThat(saved.getIsDefault()).isTrue();   // first row -> auto-default
    }

    @Test
    @DisplayName("attach_should_notMarkDefault_when_userAlreadyHasMethods")
    void attach_should_notMarkDefault_when_userAlreadyHasMethods() {
        when(providerAdapter.providerName()).thenReturn("STRIPE");
        when(providerAdapter.attachPaymentMethod("user-1", "tok"))
            .thenReturn(new PaymentProviderAdapter.AttachResult("pm_456", "4242", "VISA", 12, 2030));
        SavedPaymentMethod existing = method(7L, "user-1", true);
        when(repository.findByUserId("user-1")).thenReturn(List.of(existing));
        when(repository.save(any(SavedPaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedPaymentMethod saved = service.attach("user-1", "tok");

        assertThat(saved.getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("listForUser_should_returnRepoResult")
    void listForUser_should_returnRepoResult() {
        SavedPaymentMethod m = method(1L, "user-1", true);
        when(repository.findByUserId("user-1")).thenReturn(List.of(m));

        assertThat(service.listForUser("user-1")).containsExactly(m);
    }

    @Test
    @DisplayName("setDefault_should_flipPriorDefault_andPromoteTarget")
    void setDefault_should_flipPriorDefault_andPromoteTarget() {
        SavedPaymentMethod oldDefault = method(1L, "user-1", true);
        SavedPaymentMethod target = method(2L, "user-1", false);
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.findByUserIdAndIsDefaultTrue("user-1")).thenReturn(List.of(oldDefault));
        when(repository.save(any(SavedPaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedPaymentMethod result = service.setDefault("user-1", 2L);

        assertThat(result.getIsDefault()).isTrue();
        assertThat(oldDefault.getIsDefault()).isFalse();
        verify(repository, times(2)).save(any(SavedPaymentMethod.class));
    }

    @Test
    @DisplayName("setDefault_should_throwSecurity_when_userMismatch")
    void setDefault_should_throwSecurity_when_userMismatch() {
        SavedPaymentMethod target = method(2L, "other-user", false);
        when(repository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.setDefault("user-1", 2L))
            .isInstanceOf(SecurityException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("setDefault_should_throw_when_idMissing")
    void setDefault_should_throw_when_idMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setDefault("user-1", 99L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("delete_should_removeRow_when_userOwnsIt")
    void delete_should_removeRow_when_userOwnsIt() {
        SavedPaymentMethod target = method(1L, "user-1", false);
        when(repository.findById(1L)).thenReturn(Optional.of(target));

        service.delete("user-1", 1L);

        verify(repository).delete(target);
    }

    @Test
    @DisplayName("delete_should_throwSecurity_when_notOwner")
    void delete_should_throwSecurity_when_notOwner() {
        SavedPaymentMethod target = method(1L, "other", false);
        when(repository.findById(1L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.delete("user-1", 1L))
            .isInstanceOf(SecurityException.class);
        verify(repository, never()).delete(any());
    }

    private SavedPaymentMethod method(Long id, String userId, boolean isDefault) {
        return SavedPaymentMethod.builder()
            .id(id)
            .userId(userId)
            .provider("STRIPE")
            .providerId("pm_" + id)
            .last4("4242")
            .brand("VISA")
            .expMonth(12)
            .expYear(2030)
            .isDefault(isDefault)
            .build();
    }
}
