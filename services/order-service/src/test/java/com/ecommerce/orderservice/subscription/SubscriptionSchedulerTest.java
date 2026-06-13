package com.ecommerce.orderservice.subscription;

import com.ecommerce.orderservice.client.ProductPriceClient;
import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubscriptionScheduler} (§3.6).
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Due subscriptions trigger an OrderService.createOrder call.</li>
 *   <li>nextRunAt is advanced by intervalDays after a successful run.</li>
 *   <li>The dedup window prevents double-firing within 1 hour.</li>
 *   <li>One bad row does not block other rows in the same poll.</li>
 *   <li>The scheduler is a no-op when disabled.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionScheduler")
class SubscriptionSchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private ProductPriceClient productPriceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(
        Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC);
    private static final BigDecimal FALLBACK_PRICE = new BigDecimal("0.01");

    private SubscriptionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SubscriptionScheduler(subscriptionRepository, orderService,
            productPriceClient, objectMapper, fixedClock, true, FALLBACK_PRICE);
        lenient().when(productPriceClient.getCurrentPrice(anyString()))
            .thenReturn(Optional.of(new BigDecimal("19.99")));
    }

    @Test
    @DisplayName("should_createOrderAndAdvanceNextRun_when_subscriptionDue")
    void should_createOrderAndAdvanceNextRun_when_subscriptionDue() {
        Subscription due = dueSubscription(1L, 7, null);
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(due));
        when(orderService.createOrder(any(), any(), any(), any())).thenReturn(new Order());

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isEqualTo(1);

        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderService).createOrder(eq("user-1"), itemsCaptor.capture(),
            any(Address.class), eq(null));
        assertThat(itemsCaptor.getValue()).hasSize(1);
        assertThat(itemsCaptor.getValue().get(0).getProductId()).isEqualTo("prod-1");

        // nextRunAt was 1 hour in the past — after a 7-day advance it should
        // be 7 days minus 1 hour from "now".
        assertThat(due.getLastRunAt()).isEqualTo(now);
        assertThat(due.getNextRunAt()).isEqualTo(now.minusHours(1).plusDays(7));
        verify(subscriptionRepository).save(due);
    }

    @Test
    @DisplayName("should_priceOrderItemFromProductService_when_lookupSucceeds")
    void should_priceOrderItemFromProductService_when_lookupSucceeds() {
        Subscription due = dueSubscription(1L, 7, null);
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(due));
        when(productPriceClient.getCurrentPrice("prod-1"))
            .thenReturn(Optional.of(new BigDecimal("42.50")));
        when(orderService.createOrder(any(), any(), any(), any())).thenReturn(new Order());

        scheduler.processDueSubscriptions();

        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderService).createOrder(eq("user-1"), itemsCaptor.capture(), any(Address.class), eq(null));
        assertThat(itemsCaptor.getValue().get(0).getPrice())
            .isEqualByComparingTo(new BigDecimal("42.50"));
    }

    @Test
    @DisplayName("should_useFallbackPrice_when_productLookupReturnsEmpty")
    void should_useFallbackPrice_when_productLookupReturnsEmpty() {
        Subscription due = dueSubscription(1L, 7, null);
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(due));
        when(productPriceClient.getCurrentPrice("prod-1")).thenReturn(Optional.empty());
        when(orderService.createOrder(any(), any(), any(), any())).thenReturn(new Order());

        scheduler.processDueSubscriptions();

        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderService).createOrder(eq("user-1"), itemsCaptor.capture(), any(Address.class), eq(null));
        assertThat(itemsCaptor.getValue().get(0).getPrice()).isEqualByComparingTo(FALLBACK_PRICE);
    }

    @Test
    @DisplayName("should_skipSubscription_when_lastRunWithinDedupWindow")
    void should_skipSubscription_when_lastRunWithinDedupWindow() {
        // Last run 30 minutes ago — inside the 1-hour dedup window.
        Subscription due = dueSubscription(1L, 7, now.minusMinutes(30));
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(due));

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isZero();
        verify(orderService, never()).createOrder(any(), any(), any(), any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("should_processSubscription_when_lastRunOutsideDedupWindow")
    void should_processSubscription_when_lastRunOutsideDedupWindow() {
        // Last run 2 hours ago — outside the 1-hour dedup window.
        Subscription due = dueSubscription(1L, 7, now.minusHours(2));
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(due));
        when(orderService.createOrder(any(), any(), any(), any())).thenReturn(new Order());

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isEqualTo(1);
        verify(orderService, times(1)).createOrder(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should_continueProcessing_when_oneSubscriptionFails")
    void should_continueProcessing_when_oneSubscriptionFails() {
        Subscription badJson = dueSubscription(1L, 7, null);
        badJson.setShippingAddressJson("not-json{");
        Subscription good = dueSubscription(2L, 7, null);
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(badJson, good));
        when(orderService.createOrder(any(), any(), any(), any())).thenReturn(new Order());

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isEqualTo(1);
        verify(orderService, times(1)).createOrder(any(), any(), any(), any());
    }

    @Test
    @DisplayName("poll_should_beNoOp_when_disabled")
    void poll_should_beNoOp_when_disabled() {
        SubscriptionScheduler disabled = new SubscriptionScheduler(
            subscriptionRepository, orderService, productPriceClient, objectMapper,
            fixedClock, false, FALLBACK_PRICE);

        disabled.poll();

        verify(subscriptionRepository, never()).findByStatusAndNextRunAtBefore(any(), any());
    }

    @Test
    @DisplayName("processDueSubscriptions_should_returnZero_when_noDueRows")
    void processDueSubscriptions_should_returnZero_when_noDueRows() {
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of());

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isZero();
        verify(orderService, never()).createOrder(any(), any(), any(), any());
    }

    private Subscription dueSubscription(long id, int intervalDays, LocalDateTime lastRunAt) {
        return Subscription.builder()
            .id(id)
            .userId("user-1")
            .productId("prod-1")
            .quantity(1)
            .intervalDays(intervalDays)
            .shippingAddressJson(addressJson())
            .status(SubscriptionStatus.ACTIVE)
            .nextRunAt(now.minusHours(1))
            .lastRunAt(lastRunAt)
            .build();
    }

    private String addressJson() {
        try {
            return objectMapper.writeValueAsString(Address.builder()
                .street("1 Main")
                .city("NYC")
                .state("NY")
                .postalCode("10001")
                .country("US")
                .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
