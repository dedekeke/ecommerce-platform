package com.ecommerce.orderservice.mapper;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.dto.AdminOrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AdminOrderMapper}: the admin view MUST carry the
 * customer identity an admin needs to manage the order, and MUST NOT leak the
 * payment secret / intent that the customer-facing DTO already strips.
 */
class AdminOrderMapperTest {

    private final AdminOrderMapper mapper = new AdminOrderMapper();

    @Test
    void should_exposeCustomerIdentity_when_mappingOrder() {
        Order order = baseOrder();
        order.setUserId("user-42");
        order.setGuestOrder(false);
        order.setGuestEmail(null);

        AdminOrderResponse response = mapper.toAdminResponse(order);

        assertThat(response.userId()).isEqualTo("user-42");
        assertThat(response.orderNumber()).isEqualTo("ORD-1001");
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.total()).isEqualByComparingTo("125.50");
        assertThat(response.itemCount()).isEqualTo(1);
        assertThat(response.items()).singleElement()
            .satisfies(i -> assertThat(i.productName()).isEqualTo("Widget"));
        assertThat(response.carrier()).isEqualTo("UPS");
        assertThat(response.trackingNumber()).isEqualTo("1Z-TRACK");
    }

    @Test
    void should_exposeGuestIdentity_when_orderIsGuest() {
        Order order = baseOrder();
        order.setGuestOrder(true);
        order.setGuestEmail("guest@example.com");

        AdminOrderResponse response = mapper.toAdminResponse(order);

        assertThat(response.guestOrder()).isTrue();
        assertThat(response.guestEmail()).isEqualTo("guest@example.com");
    }

    @Test
    void should_notExposePaymentSecrets_when_serialized() throws Exception {
        Order order = baseOrder();
        order.setPaymentClientSecret("pi_secret_SHOULD_NOT_LEAK");
        order.setPaymentIntentId("pi_intent_SHOULD_NOT_LEAK");

        AdminOrderResponse response = mapper.toAdminResponse(order);

        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        String serialized = json.writeValueAsString(response);

        assertThat(serialized)
            .doesNotContain("SHOULD_NOT_LEAK")
            .doesNotContain("clientSecret")
            .doesNotContain("paymentIntentId");
    }

    private Order baseOrder() {
        OrderItem item = OrderItem.builder()
            .productId("prod-1")
            .productName("Widget")
            .price(new BigDecimal("100.00"))
            .quantity(1)
            .subtotal(new BigDecimal("100.00"))
            .build();

        Order order = Order.builder()
            .id("order-1")
            .orderNumber("ORD-1001")
            .userId("user-1")
            .status(OrderStatus.CONFIRMED)
            .subtotal(new BigDecimal("100.00"))
            .tax(new BigDecimal("8.00"))
            .shippingCost(new BigDecimal("5.99"))
            .total(new BigDecimal("125.50"))
            .carrier("UPS")
            .trackingNumber("1Z-TRACK")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .items(List.of(item))
            .build();
        item.setOrder(order);
        return order;
    }
}
