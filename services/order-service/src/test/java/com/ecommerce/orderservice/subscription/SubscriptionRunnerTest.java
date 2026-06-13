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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubscriptionRunner} — the per-subscription unit of work
 * that fires an order and advances the schedule cursor inside its own
 * transaction.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionRunner")
class SubscriptionRunnerTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private OrderService orderService;
    @Mock private ProductPriceClient productPriceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final BigDecimal FALLBACK_PRICE = new BigDecimal("0.01");
    private final LocalDateTime now = LocalDateTime.parse("2026-04-29T10:00:00");

    private SubscriptionRunner runner;

    @BeforeEach
    void setUp() {
        runner = new SubscriptionRunner(subscriptionRepository, orderService,
            productPriceClient, objectMapper, FALLBACK_PRICE);
        lenient().when(productPriceClient.getCurrentPrice(anyString()))
            .thenReturn(Optional.of(new BigDecimal("19.99")));
    }

    @Test
    @DisplayName("should_createOrderAndAdvanceNextRun_when_fired")
    void should_createOrderAndAdvanceNextRun_when_fired() {
        Subscription sub = dueSubscription(7);
        when(orderService.createOrder(any(), any(), any(), any())).thenReturn(new Order());

        runner.fireAndAdvance(sub, now);

        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderService).createOrder(eq("user-1"), itemsCaptor.capture(),
            any(Address.class), eq(null));
        assertThat(itemsCaptor.getValue()).hasSize(1);
        assertThat(itemsCaptor.getValue().get(0).getProductId()).isEqualTo("prod-1");
        // nextRunAt was 1 hour before "now" — after a 7-day advance.
        assertThat(sub.getLastRunAt()).isEqualTo(now);
        assertThat(sub.getNextRunAt()).isEqualTo(now.minusHours(1).plusDays(7));
        verify(subscriptionRepository).save(sub);
    }

    @Test
    @DisplayName("should_priceFromProductService_when_lookupSucceeds")
    void should_priceFromProductService_when_lookupSucceeds() {
        Subscription sub = dueSubscription(7);
        when(productPriceClient.getCurrentPrice("prod-1"))
            .thenReturn(Optional.of(new BigDecimal("42.50")));
        when(orderService.createOrder(any(), any(), any(), any())).thenReturn(new Order());

        runner.fireAndAdvance(sub, now);

        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderService).createOrder(eq("user-1"), itemsCaptor.capture(), any(Address.class), eq(null));
        assertThat(itemsCaptor.getValue().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("42.50"));
    }

    @Test
    @DisplayName("should_useFallbackPrice_when_productLookupReturnsEmpty")
    void should_useFallbackPrice_when_productLookupReturnsEmpty() {
        Subscription sub = dueSubscription(7);
        when(productPriceClient.getCurrentPrice("prod-1")).thenReturn(Optional.empty());
        when(orderService.createOrder(any(), any(), any(), any())).thenReturn(new Order());

        runner.fireAndAdvance(sub, now);

        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderService).createOrder(eq("user-1"), itemsCaptor.capture(), any(Address.class), eq(null));
        assertThat(itemsCaptor.getValue().get(0).getPrice()).isEqualByComparingTo(FALLBACK_PRICE);
    }

    @Test
    @DisplayName("should_throwAndNotAdvance_when_addressJsonMalformed")
    void should_throwAndNotAdvance_when_addressJsonMalformed() {
        Subscription sub = dueSubscription(7);
        sub.setShippingAddressJson("not-json{");

        assertThatThrownBy(() -> runner.fireAndAdvance(sub, now))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("malformed shippingAddressJson");

        verify(orderService, never()).createOrder(any(), any(), any(), any());
        verify(subscriptionRepository, never()).save(any());
    }

    private Subscription dueSubscription(int intervalDays) {
        return Subscription.builder()
            .id(1L)
            .userId("user-1")
            .productId("prod-1")
            .quantity(1)
            .intervalDays(intervalDays)
            .shippingAddressJson(addressJson())
            .status(SubscriptionStatus.ACTIVE)
            .nextRunAt(now.minusHours(1))
            .build();
    }

    private String addressJson() {
        try {
            return objectMapper.writeValueAsString(Address.builder()
                .street("1 Main").city("NYC").state("NY")
                .postalCode("10001").country("US").build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
