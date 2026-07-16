package com.ecommerce.paymentservice.savedmethod;

import com.ecommerce.paymentservice.domain.Payment;
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

    @Mock
    private com.ecommerce.paymentservice.service.PaymentService paymentService;

    @Mock
    private com.ecommerce.paymentservice.repository.PaymentRepository paymentRepository;

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
    @DisplayName("createSetupIntent_should_delegateToAdapter")
    void createSetupIntent_should_delegateToAdapter() {
        when(providerAdapter.createSetupIntent("user-1"))
            .thenReturn(new PaymentProviderAdapter.SetupIntentResult("seti_1", "seti_1_secret"));

        PaymentProviderAdapter.SetupIntentResult result = service.createSetupIntent("user-1");

        assertThat(result.setupIntentId()).isEqualTo("seti_1");
        assertThat(result.clientSecret()).isEqualTo("seti_1_secret");
    }

    @Test
    @DisplayName("confirmSetupIntent_should_persistSafeFields_when_succeededAndOwned")
    void confirmSetupIntent_should_persistSafeFields_when_succeededAndOwned() {
        when(providerAdapter.providerName()).thenReturn("STRIPE");
        when(providerAdapter.retrieveSetupIntent("seti_1")).thenReturn(
            new PaymentProviderAdapter.SetupIntentDetails(
                "seti_1", "succeeded", "user-1", "pm_9", "4242", "VISA", 11, 2031));
        when(repository.findByUserIdAndProviderId("user-1", "pm_9")).thenReturn(Optional.empty());
        when(repository.findByUserId("user-1")).thenReturn(List.of());
        when(repository.saveAndFlush(any(SavedPaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedPaymentMethod saved = service.confirmSetupIntent("user-1", "seti_1");

        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getProviderId()).isEqualTo("pm_9");
        assertThat(saved.getLast4()).isEqualTo("4242");
        assertThat(saved.getBrand()).isEqualTo("VISA");
        assertThat(saved.getExpMonth()).isEqualTo(11);
        assertThat(saved.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("confirmSetupIntent_should_throwSecurity_when_metadataUserMismatch")
    void confirmSetupIntent_should_throwSecurity_when_metadataUserMismatch() {
        when(providerAdapter.retrieveSetupIntent("seti_x")).thenReturn(
            new PaymentProviderAdapter.SetupIntentDetails(
                "seti_x", "succeeded", "attacker", "pm_evil", "4242", "VISA", 1, 2030));

        assertThatThrownBy(() -> service.confirmSetupIntent("user-1", "seti_x"))
            .isInstanceOf(SecurityException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("confirmSetupIntent_should_throwState_when_notSucceeded")
    void confirmSetupIntent_should_throwState_when_notSucceeded() {
        when(providerAdapter.retrieveSetupIntent("seti_p")).thenReturn(
            new PaymentProviderAdapter.SetupIntentDetails(
                "seti_p", "requires_payment_method", "user-1", null, null, null, null, null));

        assertThatThrownBy(() -> service.confirmSetupIntent("user-1", "seti_p"))
            .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("confirmSetupIntent_should_beIdempotent_when_methodAlreadySaved")
    void confirmSetupIntent_should_beIdempotent_when_methodAlreadySaved() {
        SavedPaymentMethod existing = method(3L, "user-1", true);
        when(providerAdapter.retrieveSetupIntent("seti_1")).thenReturn(
            new PaymentProviderAdapter.SetupIntentDetails(
                "seti_1", "succeeded", "user-1", "pm_3", "4242", "VISA", 12, 2030));
        when(repository.findByUserIdAndProviderId("user-1", "pm_3")).thenReturn(Optional.of(existing));

        SavedPaymentMethod result = service.confirmSetupIntent("user-1", "seti_1");

        assertThat(result).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("persistFromWebhook_should_resolveCard_andPersistIdempotently")
    void persistFromWebhook_should_resolveCard_andPersistIdempotently() {
        when(providerAdapter.providerName()).thenReturn("STRIPE");
        when(providerAdapter.attachPaymentMethod("user-1", "pm_7"))
            .thenReturn(new PaymentProviderAdapter.AttachResult("pm_7", "1111", "AMEX", 6, 2029));
        when(repository.findByUserIdAndProviderId("user-1", "pm_7")).thenReturn(Optional.empty());
        when(repository.findByUserId("user-1")).thenReturn(List.of());
        when(repository.saveAndFlush(any(SavedPaymentMethod.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedPaymentMethod saved = service.persistFromWebhook("user-1", "pm_7");

        assertThat(saved.getProviderId()).isEqualTo("pm_7");
        assertThat(saved.getBrand()).isEqualTo("AMEX");
        assertThat(saved.getLast4()).isEqualTo("1111");
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
            .isInstanceOf(SavedPaymentMethodNotFoundException.class);
    }

    @Test
    @DisplayName("payWithSavedMethod_should_confirm_when_callerOwnsMethodAndOrder")
    void payWithSavedMethod_should_confirm_when_callerOwnsMethodAndOrder() {
        SavedPaymentMethod owned = method(4L, "user-1", true);
        Payment order = Payment.builder().userId("user-1").paymentIntentId("pi_1").build();
        Payment confirmed = new Payment();
        when(repository.findByUserIdAndProviderId("user-1", "pm_4")).thenReturn(Optional.of(owned));
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(Optional.of(order));
        when(paymentService.confirmPayment("pi_1", "pm_4")).thenReturn(confirmed);

        Payment result = service.payWithSavedMethod("user-1", "pi_1", "pm_4");

        assertThat(result).isSameAs(confirmed);
        verify(paymentService).confirmPayment("pi_1", "pm_4");
    }

    @Test
    @DisplayName("payWithSavedMethod_should_rejectAndNotCharge_when_methodNotCallers")
    void payWithSavedMethod_should_rejectAndNotCharge_when_methodNotCallers() {
        // The pm id is real but belongs to another user -> not in THIS caller's vault.
        when(repository.findByUserIdAndProviderId("attacker", "pm_victim")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payWithSavedMethod("attacker", "pi_1", "pm_victim"))
            .isInstanceOf(SecurityException.class);
        // Critical: no gateway charge is ever attempted for an unowned method.
        verify(paymentService, never()).confirmPayment(any(), any());
    }

    @Test
    @DisplayName("payWithSavedMethod_should_rejectAndNotCharge_when_orderBelongsToAnotherUser")
    void payWithSavedMethod_should_rejectAndNotCharge_when_orderBelongsToAnotherUser() {
        // Caller owns the saved card, but the target PaymentIntent is a STRANGER's order.
        SavedPaymentMethod owned = method(4L, "user-1", true);
        Payment strangersOrder = Payment.builder().userId("victim").paymentIntentId("pi_victim").build();
        when(repository.findByUserIdAndProviderId("user-1", "pm_4")).thenReturn(Optional.of(owned));
        when(paymentRepository.findByPaymentIntentId("pi_victim")).thenReturn(Optional.of(strangersOrder));

        assertThatThrownBy(() -> service.payWithSavedMethod("user-1", "pi_victim", "pm_4"))
            .isInstanceOf(SecurityException.class);
        verify(paymentService, never()).confirmPayment(any(), any());
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
