package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.IdempotencyKey;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.IdempotencyStatus;
import com.ecommerce.orderservice.dto.CheckoutRequest;
import com.ecommerce.orderservice.dto.CheckoutResponse;
import com.ecommerce.orderservice.dto.GuestCheckoutRequest;
import com.ecommerce.orderservice.exception.ConcurrentCheckoutException;
import com.ecommerce.orderservice.saga.OrderCreationSaga;
import com.ecommerce.orderservice.saga.OrderCreationSaga.CheckoutResult;
import com.ecommerce.orderservice.security.GuestIdentityFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Orchestrates the REST checkout: drives the {@link OrderCreationSaga} (the real
 * cart → inventory → order → payment flow) and enforces Idempotency-Key
 * semantics so a replayed or double-submitted checkout never creates a duplicate
 * order.
 *
 * <p>Deliberately NOT {@code @Transactional}: the idempotency reservation must
 * commit before the saga runs (so a concurrent request sees it), and the release
 * on failure must persist independently of the saga's rolled-back transaction.</p>
 *
 * <p>Currency is USD (matching the saga's PaymentIntent) and surfaced so the
 * frontend renders totals without assuming a currency.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final OrderCreationSaga orderCreationSaga;
    private final IdempotencyService idempotencyService;
    private final OrderService orderService;
    private final GuestIdentityFactory guestIdentityFactory;

    /** Result of a checkout: the response plus whether it was an idempotent replay (HTTP 200 vs 201). */
    public record Outcome(CheckoutResponse response, boolean replay) {
    }

    /**
     * Authenticated checkout. The owner is the JWT subject (resolved upstream);
     * the saga reads that user's server-side cart.
     */
    public Outcome checkout(String userId, String idempotencyKey, CheckoutRequest request) {
        Address shippingAddress = request.shippingAddress().toDomain();
        return runIdempotentCheckout(
            userId,
            idempotencyKey,
            () -> orderCreationSaga.executeCheckout(
                userId, shippingAddress, request.promotionCode(),
                request.userEmail(), request.userName()),
            null
        );
    }

    /**
     * Guest checkout. The owner identity is derived server-side from the email
     * (see {@link GuestIdentityFactory}) — never asserted by the client — so the
     * authenticated path's identity guarantees cannot be bypassed here. The saga
     * reads the guest's cart keyed by that same derived identity; once the order
     * exists it is flagged as a guest order and stamped with the claim key.
     *
     * <p>Same Idempotency-Key + clientSecret contract as {@link #checkout}: the
     * key is scoped to the derived guest identity, so a retried guest submit is
     * deduped exactly like an authenticated one.</p>
     */
    public Outcome guestCheckout(String idempotencyKey, GuestCheckoutRequest request) {
        String normalizedEmail = guestIdentityFactory.normalizeEmail(request.email());
        String guestId = guestIdentityFactory.guestIdForNormalizedEmail(normalizedEmail);
        Address shippingAddress = request.shippingAddress().toDomain();
        log.info("Guest checkout for derived identity {} (idempotencyKey present: {})",
            guestId, StringUtils.hasText(idempotencyKey));
        return runIdempotentCheckout(
            guestId,
            idempotencyKey,
            () -> orderCreationSaga.executeCheckout(
                guestId, shippingAddress, request.promotionCode(),
                normalizedEmail, request.userName()),
            orderId -> orderService.markAsGuestOrder(orderId, normalizedEmail)
        );
    }

    /**
     * Shared Idempotency-Key orchestration for both checkout flows. {@code owner}
     * is the JWT subject (authenticated) or the derived guest identity;
     * {@code sagaCall} runs the real order-creation saga; {@code onOrderCreated}
     * (nullable) runs once against a freshly created order id BEFORE the key is
     * marked COMPLETED — used by the guest flow to flag the order and persist the
     * claim key. It is not run on an idempotent replay (already applied).
     */
    private Outcome runIdempotentCheckout(
        String owner,
        String idempotencyKey,
        Supplier<CheckoutResult> sagaCall,
        Consumer<String> onOrderCreated
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            log.info("Checkout for owner {} without idempotency key", owner);
            CheckoutResult result = sagaCall.get();
            applyOnCreated(onOrderCreated, result);
            return new Outcome(CheckoutResponse.from(result), false);
        }

        Optional<IdempotencyKey> existing = idempotencyService.find(owner, idempotencyKey);
        if (existing.isPresent()) {
            return replayOutcome(owner, existing.get());
        }

        if (!idempotencyService.tryReserve(owner, idempotencyKey)) {
            // Lost the insert race to a concurrent request with the same key.
            IdempotencyKey now = idempotencyService.find(owner, idempotencyKey)
                .orElseThrow(() -> new ConcurrentCheckoutException(
                    "Checkout is already being processed for this idempotency key"));
            return replayOutcome(owner, now);
        }

        CheckoutResult result;
        try {
            result = sagaCall.get();
            applyOnCreated(onOrderCreated, result);
        } catch (RuntimeException ex) {
            // Release so a genuine retry (axios-retry on 5xx) can create the order.
            idempotencyService.release(owner, idempotencyKey);
            throw ex;
        }
        idempotencyService.complete(owner, idempotencyKey, result.order().getId());
        return new Outcome(CheckoutResponse.from(result), false);
    }

    private void applyOnCreated(Consumer<String> onOrderCreated, CheckoutResult result) {
        if (onOrderCreated != null) {
            onOrderCreated.accept(result.order().getId());
        }
    }

    private Outcome replayOutcome(String userId, IdempotencyKey record) {
        if (record.getStatus() == IdempotencyStatus.COMPLETED && record.getOrderId() != null) {
            log.info("Idempotent replay for user {} -> returning existing order {}", userId, record.getOrderId());
            Order order = orderService.getOrder(record.getOrderId(), userId);
            return new Outcome(CheckoutResponse.fromExistingOrder(order, "USD"), true);
        }
        throw new ConcurrentCheckoutException(
            "Checkout is already being processed for this idempotency key");
    }
}
