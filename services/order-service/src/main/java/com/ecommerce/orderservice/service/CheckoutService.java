package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.IdempotencyKey;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.IdempotencyStatus;
import com.ecommerce.orderservice.dto.CheckoutRequest;
import com.ecommerce.orderservice.dto.CheckoutResponse;
import com.ecommerce.orderservice.exception.ConcurrentCheckoutException;
import com.ecommerce.orderservice.saga.OrderCreationSaga;
import com.ecommerce.orderservice.saga.OrderCreationSaga.CheckoutResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

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

    /** Result of a checkout: the response plus whether it was an idempotent replay (HTTP 200 vs 201). */
    public record Outcome(CheckoutResponse response, boolean replay) {
    }

    public Outcome checkout(String userId, String idempotencyKey, CheckoutRequest request) {
        Address shippingAddress = request.shippingAddress().toDomain();

        if (!StringUtils.hasText(idempotencyKey)) {
            log.info("Checkout for user {} without idempotency key", userId);
            return new Outcome(CheckoutResponse.from(runSaga(userId, shippingAddress, request)), false);
        }

        Optional<IdempotencyKey> existing = idempotencyService.find(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replayOutcome(userId, existing.get());
        }

        if (!idempotencyService.tryReserve(userId, idempotencyKey)) {
            // Lost the insert race to a concurrent request with the same key.
            IdempotencyKey now = idempotencyService.find(userId, idempotencyKey)
                .orElseThrow(() -> new ConcurrentCheckoutException(
                    "Checkout is already being processed for this idempotency key"));
            return replayOutcome(userId, now);
        }

        CheckoutResult result;
        try {
            result = runSaga(userId, shippingAddress, request);
        } catch (RuntimeException ex) {
            // Release so a genuine retry (axios-retry on 5xx) can create the order.
            idempotencyService.release(userId, idempotencyKey);
            throw ex;
        }
        idempotencyService.complete(userId, idempotencyKey, result.order().getId());
        return new Outcome(CheckoutResponse.from(result), false);
    }

    private CheckoutResult runSaga(String userId, Address shippingAddress, CheckoutRequest request) {
        return orderCreationSaga.executeCheckout(
            userId,
            shippingAddress,
            request.promotionCode(),
            request.userEmail(),
            request.userName()
        );
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
