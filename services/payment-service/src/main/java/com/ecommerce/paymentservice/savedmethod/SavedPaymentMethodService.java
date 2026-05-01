package com.ecommerce.paymentservice.savedmethod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Saved payment-method service (§3.9).
 *
 * <p>Owns the persistence concerns; delegates token tokenization to a
 * {@link PaymentProviderAdapter}. The first row attached for a user is
 * automatically marked default; calling {@link #setDefault(String, Long)}
 * later flips the chosen row to default and clears the flag on every other
 * row owned by the same user (single-default invariant).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavedPaymentMethodService {

    private final SavedPaymentMethodRepository repository;
    private final PaymentProviderAdapter providerAdapter;

    @Transactional
    public SavedPaymentMethod attach(String userId, String token) {
        PaymentProviderAdapter.AttachResult attached = providerAdapter.attachPaymentMethod(userId, token);
        boolean firstForUser = repository.findByUserId(userId).isEmpty();
        SavedPaymentMethod entity = SavedPaymentMethod.builder()
            .userId(userId)
            .provider(providerAdapter.providerName())
            .providerId(attached.providerId())
            .last4(attached.last4())
            .brand(attached.brand())
            .expMonth(attached.expMonth())
            .expYear(attached.expYear())
            .isDefault(firstForUser)
            .build();
        SavedPaymentMethod saved = repository.save(entity);
        log.info("Attached saved payment method id={} userId={} provider={} default={}",
            saved.getId(), userId, saved.getProvider(), saved.getIsDefault());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SavedPaymentMethod> listForUser(String userId) {
        return repository.findByUserId(userId);
    }

    @Transactional
    public SavedPaymentMethod setDefault(String userId, Long id) {
        SavedPaymentMethod target = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Saved payment method not found: " + id));
        if (!target.getUserId().equals(userId)) {
            throw new SecurityException("User " + userId + " does not own payment method " + id);
        }
        // Flip the previous default(s) off — there should be at most one but
        // the loop guards against historical drift.
        for (SavedPaymentMethod existing : repository.findByUserIdAndIsDefaultTrue(userId)) {
            if (!existing.getId().equals(id)) {
                existing.setIsDefault(false);
                repository.save(existing);
            }
        }
        target.setIsDefault(true);
        return repository.save(target);
    }

    @Transactional
    public void delete(String userId, Long id) {
        SavedPaymentMethod target = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Saved payment method not found: " + id));
        if (!target.getUserId().equals(userId)) {
            throw new SecurityException("User " + userId + " does not own payment method " + id);
        }
        repository.delete(target);
        log.info("Deleted saved payment method id={} userId={}", id, userId);
    }
}
