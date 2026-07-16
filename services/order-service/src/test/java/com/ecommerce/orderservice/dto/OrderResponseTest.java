package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderResponse} — the client-safe order view.
 *
 * <p>Guards the P2 fix: the DTO must expose only client-safe fields and must
 * never carry the Stripe {@code paymentClientSecret} or the ownership/PII
 * columns ({@code userId}, {@code guestEmail}) that the raw entity exposed.</p>
 */
class OrderResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static Order fullOrder() {
        Order order = Order.builder()
            .id("order-1")
            .orderNumber("ORD-1")
            .userId("auth0|user-a")
            .status(OrderStatus.CONFIRMED)
            .subtotal(new BigDecimal("100.00"))
            .tax(new BigDecimal("8.00"))
            .shippingCost(new BigDecimal("5.00"))
            .discountAmount(new BigDecimal("10.00"))
            .loyaltyDiscount(new BigDecimal("2.00"))
            .total(new BigDecimal("101.00"))
            .paymentIntentId("pi_123")
            .paymentClientSecret("pi_123_secret_SHOULD_NOT_LEAK")
            .guestOrder(true)
            .guestEmail("guest@example.com")
            .shippingAddress(Address.builder()
                .street("1 Main St").city("SF").state("CA")
                .postalCode("94105").country("USA").build())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        OrderItem item = OrderItem.builder()
            .productId("p-1").productName("Widget")
            .price(new BigDecimal("50.00")).quantity(2)
            .subtotal(new BigDecimal("100.00"))
            .build();
        order.setItems(List.of(item));
        return order;
    }

    @Test
    void should_mapAllClientSafeFields_when_fromOrder() {
        Order order = fullOrder();

        OrderResponse response = OrderResponse.from(order);

        assertThat(response.orderId()).isEqualTo("order-1");
        assertThat(response.orderNumber()).isEqualTo("ORD-1");
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.subtotal()).isEqualByComparingTo("100.00");
        assertThat(response.tax()).isEqualByComparingTo("8.00");
        assertThat(response.shippingCost()).isEqualByComparingTo("5.00");
        assertThat(response.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(response.loyaltyDiscount()).isEqualByComparingTo("2.00");
        assertThat(response.total()).isEqualByComparingTo("101.00");
        assertThat(response.paymentIntentId()).isEqualTo("pi_123");
        assertThat(response.guestOrder()).isTrue();
    }

    @Test
    void should_mapItems_when_fromOrder() {
        OrderResponse response = OrderResponse.from(fullOrder());

        assertThat(response.items()).hasSize(1);
        OrderResponse.Item item = response.items().get(0);
        assertThat(item.productId()).isEqualTo("p-1");
        assertThat(item.productName()).isEqualTo("Widget");
        assertThat(item.price()).isEqualByComparingTo("50.00");
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.subtotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void should_mapShippingAddress_when_fromOrder() {
        OrderResponse.ShippingAddress address = OrderResponse.from(fullOrder()).shippingAddress();

        assertThat(address.street()).isEqualTo("1 Main St");
        assertThat(address.city()).isEqualTo("SF");
        assertThat(address.state()).isEqualTo("CA");
        assertThat(address.postalCode()).isEqualTo("94105");
        assertThat(address.country()).isEqualTo("USA");
    }

    @Test
    void should_notExposeSecretOrPiiComponents_inRecordShape() {
        Set<String> componentNames = List.of(OrderResponse.class.getRecordComponents()).stream()
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());

        assertThat(componentNames)
            .doesNotContain("paymentClientSecret", "clientSecret", "userId", "guestEmail");
    }

    @Test
    void should_notSerializeSecretOrPii_when_writtenAsJson() throws Exception {
        String json = objectMapper.writeValueAsString(OrderResponse.from(fullOrder()));

        assertThat(json)
            .doesNotContain("pi_123_secret_SHOULD_NOT_LEAK")
            .doesNotContain("paymentClientSecret")
            .doesNotContain("clientSecret")
            .doesNotContain("guest@example.com")
            .doesNotContain("guestEmail")
            .doesNotContain("\"userId\"");
    }

    @Test
    void should_returnEmptyItems_when_orderHasNoItems() {
        Order order = fullOrder();
        order.setItems(null);

        assertThat(OrderResponse.from(order).items()).isEmpty();
    }

    @Test
    void should_returnNullShippingAddress_when_orderHasNoAddress() {
        Order order = fullOrder();
        order.setShippingAddress(null);

        assertThat(OrderResponse.from(order).shippingAddress()).isNull();
    }
}
