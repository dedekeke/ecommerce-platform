package com.ecommerce.paymentservice.savedmethod;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.service.PaymentNotFoundException;
import com.ecommerce.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

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

    /**
     * Start the secure add-a-card flow: ask the provider for a SetupIntent whose
     * client secret the browser uses to save a card via the provider SDK. The raw
     * PAN never reaches this service (PCI SAQ-A); we only ever see the resulting
     * tokenized payment method later, at confirm/webhook time.
     */
    public PaymentProviderAdapter.SetupIntentResult createSetupIntent(String userId) {
        PaymentProviderAdapter.SetupIntentResult result = providerAdapter.createSetupIntent(userId);
        log.info("Created setup intent {} for user={}", result.setupIntentId(), userId);
        return result;
    }

    /**
     * Confirm the add-a-card flow after the browser saved the card with the
     * provider. Reads the SetupIntent back from the provider (authoritative) and
     * only persists when it (a) succeeded and (b) is bound — via metadata — to the
     * calling user. This guards against a client submitting a SetupIntent id it does
     * not own (IDOR). Persistence is idempotent so a later webhook redelivery for the
     * same intent does not create a duplicate row.
     */
    @Transactional
    public SavedPaymentMethod confirmSetupIntent(String userId, String setupIntentId) {
        PaymentProviderAdapter.SetupIntentDetails details = providerAdapter.retrieveSetupIntent(setupIntentId);
        if (!"succeeded".equalsIgnoreCase(details.status())) {
            throw new IllegalStateException("SetupIntent " + setupIntentId + " is not completed: " + details.status());
        }
        if (details.userId() == null || !details.userId().equals(userId)) {
            throw new SecurityException("SetupIntent " + setupIntentId + " does not belong to user " + userId);
        }
        if (details.paymentMethodId() == null) {
            throw new IllegalStateException("SetupIntent " + setupIntentId + " carries no payment method");
        }
        return persistIdempotent(userId, details.paymentMethodId(),
            details.last4(), details.brand(), details.expMonth(), details.expYear());
    }

    /**
     * Persist a saved payment method from an authoritative {@code setup_intent.succeeded}
     * webhook. The owning user comes from the intent metadata (set at creation), and the
     * card display fields are resolved from the provider. Idempotent per (user, providerId).
     */
    @Transactional
    public SavedPaymentMethod persistFromWebhook(String userId, String paymentMethodId) {
        PaymentProviderAdapter.AttachResult attached = providerAdapter.attachPaymentMethod(userId, paymentMethodId);
        return persistIdempotent(userId, attached.providerId(),
            attached.last4(), attached.brand(), attached.expMonth(), attached.expYear());
    }

    /**
     * Insert a saved method unless one with the same (userId, providerId) already
     * exists — the confirm endpoint and the webhook both converge here, so whichever
     * arrives second is a no-op that returns the existing row. A race that slips past
     * the pre-check is caught on the unique-constraint violation and re-read.
     */
    private SavedPaymentMethod persistIdempotent(String userId, String providerId,
            String last4, String brand, Integer expMonth, Integer expYear) {
        return repository.findByUserIdAndProviderId(userId, providerId)
            .orElseGet(() -> {
                boolean firstForUser = repository.findByUserId(userId).isEmpty();
                SavedPaymentMethod entity = SavedPaymentMethod.builder()
                    .userId(userId)
                    .provider(providerAdapter.providerName())
                    .providerId(providerId)
                    .last4(last4)
                    .brand(brand)
                    .expMonth(expMonth)
                    .expYear(expYear)
                    .isDefault(firstForUser)
                    .build();
                try {
                    SavedPaymentMethod saved = repository.saveAndFlush(entity);
                    log.info("Saved payment method id={} userId={} providerId={} default={}",
                        saved.getId(), userId, providerId, saved.getIsDefault());
                    return saved;
                } catch (DataIntegrityViolationException raced) {
                    log.info("Concurrent save for userId={} providerId={}; returning existing row", userId, providerId);
                    return repository.findByUserIdAndProviderId(userId, providerId)
                        .orElseThrow(() -> raced);
                }
            });
    }

    @Transactional(readOnly = true)
    public List<SavedPaymentMethod> listForUser(String userId) {
        return repository.findByUserId(userId);
    }

    /**
     * Pay for an order's PaymentIntent with one of the caller's OWN saved methods.
     *
     * <p>This is the authoritative ownership gate for saved-card checkout. BOTH the payment
     * method AND the order must belong to the authenticated caller before we confirm
     * server-side:</p>
     * <ol>
     *   <li>the (userId, providerId) row must be in the caller's vault — a leaked/guessed
     *       {@code pm_...} cannot charge someone else's card; and</li>
     *   <li>the target PaymentIntent's {@code userId} must equal the caller — an authenticated
     *       user cannot pay a stranger's order with their own saved card.</li>
     * </ol>
     * <p>Either mismatch throws {@link SecurityException} (HTTP 403) before any gateway call.</p>
     */
    @Transactional
    public Payment payWithSavedMethod(String userId, String paymentIntentId, String providerId) {
        // (1) The saved method must belong to the caller.
        repository.findByUserIdAndProviderId(userId, providerId)
            .orElseThrow(() -> new SecurityException(
                "Saved payment method does not belong to user " + userId));
        // (2) The order/PaymentIntent being confirmed must also belong to the caller.
        Payment payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
            .orElseThrow(() -> new PaymentNotFoundException(
                "Payment not found for intent: " + paymentIntentId));
        if (!userId.equals(payment.getUserId())) {
            throw new SecurityException(
                "Payment intent " + paymentIntentId + " does not belong to user " + userId);
        }
        log.info("Confirming payment intent {} with saved method (user={})", paymentIntentId, userId);
        return paymentService.confirmPayment(paymentIntentId, providerId);
    }

    @Transactional
    public SavedPaymentMethod setDefault(String userId, Long id) {
        SavedPaymentMethod target = repository.findById(id)
            .orElseThrow(() -> new SavedPaymentMethodNotFoundException("Saved payment method not found: " + id));
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
            .orElseThrow(() -> new SavedPaymentMethodNotFoundException("Saved payment method not found: " + id));
        if (!target.getUserId().equals(userId)) {
            throw new SecurityException("User " + userId + " does not own payment method " + id);
        }
        repository.delete(target);
        log.info("Deleted saved payment method id={} userId={}", id, userId);
    }
}
