package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.IdempotencyKey;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.IdempotencyStatus;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.dto.AddressDto;
import com.ecommerce.orderservice.dto.CheckoutRequest;
import com.ecommerce.orderservice.exception.ConcurrentCheckoutException;
import com.ecommerce.orderservice.saga.OrderCreationSaga;
import com.ecommerce.orderservice.saga.OrderCreationSaga.CheckoutResult;
import com.ecommerce.orderservice.saga.OrderCreationSaga.SagaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CheckoutService}: verifies the saga is invoked with the
 * real request (never a hardcoded product) and that Idempotency-Key semantics
 * guarantee at most one order per key.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    private static final String USER_ID = "user-123";
    private static final String KEY = "idem-key-abc";
    private static final String ORDER_ID = "order-xyz";

    @Mock
    private OrderCreationSaga orderCreationSaga;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private CheckoutService checkoutService;

    // ---- no idempotency key -------------------------------------------------

    @Test
    void should_invokeSagaWithRealRequest_when_noIdempotencyKey() {
        when(orderCreationSaga.executeCheckout(eq(USER_ID), any(), eq("SAVE10"),
            eq("buyer@example.com"), eq("Buyer")))
            .thenReturn(freshResult());

        CheckoutService.Outcome outcome = checkoutService.checkout(USER_ID, null, request());

        assertFalse(outcome.replay());
        assertEquals("pi_123_secret", outcome.response().clientSecret());
        assertEquals(ORDER_ID, outcome.response().orderId());
        verifyNoInteractions(idempotencyService);

        ArgumentCaptor<Address> addressCaptor = ArgumentCaptor.forClass(Address.class);
        verify(orderCreationSaga).executeCheckout(eq(USER_ID), addressCaptor.capture(),
            eq("SAVE10"), eq("buyer@example.com"), eq("Buyer"));
        assertEquals("1 Main St", addressCaptor.getValue().getStreet());
    }

    // ---- fresh idempotent checkout -----------------------------------------

    @Test
    void should_reserveRunAndComplete_when_newIdempotencyKey() {
        when(idempotencyService.find(USER_ID, KEY)).thenReturn(Optional.empty());
        when(idempotencyService.tryReserve(USER_ID, KEY)).thenReturn(true);
        when(orderCreationSaga.executeCheckout(eq(USER_ID), any(), any(), any(), any()))
            .thenReturn(freshResult());

        CheckoutService.Outcome outcome = checkoutService.checkout(USER_ID, KEY, request());

        assertFalse(outcome.replay());
        assertEquals("pi_123_secret", outcome.response().clientSecret());
        verify(idempotencyService).complete(USER_ID, KEY, ORDER_ID);
        verify(idempotencyService, never()).release(any(), any());
    }

    // ---- idempotent replay --------------------------------------------------

    @Test
    void should_returnSameOrderWithoutRunningSaga_when_keyAlreadyCompleted() {
        IdempotencyKey completed = keyRecord(IdempotencyStatus.COMPLETED, ORDER_ID);
        when(idempotencyService.find(USER_ID, KEY)).thenReturn(Optional.of(completed));
        when(orderService.getOrder(ORDER_ID, USER_ID)).thenReturn(persistedOrder());

        CheckoutService.Outcome outcome = checkoutService.checkout(USER_ID, KEY, request());

        assertTrue(outcome.replay());
        assertEquals(ORDER_ID, outcome.response().orderId());
        // Replay must not re-run the saga, but must re-serve the persisted secret
        // so the owning session can still complete payment.
        assertEquals("pi_123_secret", outcome.response().clientSecret());
        verify(orderCreationSaga, never()).executeCheckout(any(), any(), any(), any(), any());
        verify(idempotencyService, never()).tryReserve(any(), any());
    }

    @Test
    void should_returnExistingOrder_when_reservationLostButAlreadyCompleted() {
        when(idempotencyService.find(USER_ID, KEY))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(keyRecord(IdempotencyStatus.COMPLETED, ORDER_ID)));
        when(idempotencyService.tryReserve(USER_ID, KEY)).thenReturn(false);
        when(orderService.getOrder(ORDER_ID, USER_ID)).thenReturn(persistedOrder());

        CheckoutService.Outcome outcome = checkoutService.checkout(USER_ID, KEY, request());

        assertTrue(outcome.replay());
        verify(orderCreationSaga, never()).executeCheckout(any(), any(), any(), any(), any());
    }

    // ---- concurrency + failure ---------------------------------------------

    @Test
    void should_throwConcurrent_when_keyStillInProgress() {
        when(idempotencyService.find(USER_ID, KEY))
            .thenReturn(Optional.of(keyRecord(IdempotencyStatus.IN_PROGRESS, null)));

        assertThrows(ConcurrentCheckoutException.class,
            () -> checkoutService.checkout(USER_ID, KEY, request()));

        verify(orderCreationSaga, never()).executeCheckout(any(), any(), any(), any(), any());
    }

    @Test
    void should_releaseKeyAndRethrow_when_sagaFails() {
        when(idempotencyService.find(USER_ID, KEY)).thenReturn(Optional.empty());
        when(idempotencyService.tryReserve(USER_ID, KEY)).thenReturn(true);
        when(orderCreationSaga.executeCheckout(eq(USER_ID), any(), any(), any(), any()))
            .thenThrow(new SagaException("payment gateway down"));

        assertThrows(SagaException.class,
            () -> checkoutService.checkout(USER_ID, KEY, request()));

        verify(idempotencyService).release(USER_ID, KEY);
        verify(idempotencyService, never()).complete(any(), any(), isNull());
    }

    // ---- fixtures -----------------------------------------------------------

    private CheckoutRequest request() {
        return new CheckoutRequest(
            USER_ID,
            new AddressDto("1 Main St", "SF", "CA", "94105", "USA"),
            "SAVE10",
            "buyer@example.com",
            "Buyer"
        );
    }

    private CheckoutResult freshResult() {
        return new CheckoutResult(persistedOrder(), "pi_123", "pi_123_secret", "USD");
    }

    private Order persistedOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNumber("ORD-2026-0001");
        order.setUserId(USER_ID);
        order.setStatus(OrderStatus.PENDING);
        order.setSubtotal(new BigDecimal("59.98"));
        order.setTax(new BigDecimal("4.80"));
        order.setShippingCost(new BigDecimal("5.99"));
        order.setTotal(new BigDecimal("70.77"));
        order.setPaymentIntentId("pi_123");
        order.setPaymentClientSecret("pi_123_secret");
        return order;
    }

    private IdempotencyKey keyRecord(IdempotencyStatus status, String orderId) {
        return IdempotencyKey.builder()
            .id("idk-1")
            .userId(USER_ID)
            .idempotencyKey(KEY)
            .status(status)
            .orderId(orderId)
            .build();
    }
}
