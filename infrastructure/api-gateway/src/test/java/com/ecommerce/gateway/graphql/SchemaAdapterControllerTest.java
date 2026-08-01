package com.ecommerce.gateway.graphql;

import com.ecommerce.gateway.graphql.controller.SchemaAdapterController;
import com.ecommerce.gateway.graphql.dto.CartDto;
import com.ecommerce.gateway.graphql.dto.CartItemDto;
import com.ecommerce.gateway.graphql.dto.OrderDto;
import com.ecommerce.gateway.graphql.dto.OrderItemDto;
import com.ecommerce.gateway.graphql.dto.PageDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import com.ecommerce.gateway.graphql.dto.PromotionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage of the field-by-field adapter so the null-defaulting
 * branches all light up under JaCoCo without going through the GraphQL engine.
 */
class SchemaAdapterControllerTest {

    private final SchemaAdapterController adapter = new SchemaAdapterController();

    @Test
    void should_defaultProductPriceToZero_when_priceMissing() {
        ProductDto p = new ProductDto("1", "n", null, null, null, null, null, null, null);
        assertThat(adapter.productPrice(p)).isEqualTo(0.0);
        assertThat(adapter.productCurrency(p)).isEqualTo("USD");
        assertThat(adapter.productImages(p)).isEmpty();
        assertThat(adapter.productLiveStockQty(p)).isNull();
        assertThat(adapter.productInStock(p)).isFalse();
    }

    @Test
    void should_useUpstreamProductValues_when_present() {
        ProductDto p = new ProductDto("1", "n", null, new BigDecimal("3.50"), "EUR",
                List.of("a"), null, 4, true);
        assertThat(adapter.productPrice(p)).isEqualTo(3.5);
        assertThat(adapter.productCurrency(p)).isEqualTo("EUR");
        assertThat(adapter.productImages(p)).containsExactly("a");
        assertThat(adapter.productLiveStockQty(p)).isEqualTo(4);
    }

    @Test
    void should_defaultCartFields_when_nullsPresent() {
        CartDto c = new CartDto("u", null, null, null, null);
        assertThat(adapter.cartSubtotal(c)).isEqualTo(0.0);
        assertThat(adapter.cartItemCount(c)).isZero();
        assertThat(adapter.cartItems(c)).isEmpty();
        assertThat(adapter.cartPromotions(c)).isEmpty();
    }

    @Test
    void should_returnUpstreamCartValues_when_present() {
        CartDto c = new CartDto("u",
                List.of(new CartItemDto("p", 1, BigDecimal.ONE, BigDecimal.ONE)),
                new BigDecimal("9.50"), 3,
                List.of(new PromotionDto("X", "d", new BigDecimal("0.5"))));
        assertThat(adapter.cartSubtotal(c)).isEqualTo(9.5);
        assertThat(adapter.cartItemCount(c)).isEqualTo(3);
        assertThat(adapter.cartItems(c)).hasSize(1);
        assertThat(adapter.cartPromotions(c)).hasSize(1);
    }

    @Test
    void should_defaultCartItemFields_when_nullsPresent() {
        CartItemDto i = new CartItemDto("p", 1, null, null);
        assertThat(adapter.cartItemUnitPrice(i)).isEqualTo(0.0);
        assertThat(adapter.cartItemSubtotal(i)).isEqualTo(0.0);
    }

    @Test
    void should_defaultOrderFields_when_nullsPresent() {
        OrderDto o = new OrderDto("o", "ord", "u", "PAID", null, null, "t");
        assertThat(adapter.orderItems(o)).isEmpty();
        assertThat(adapter.orderTotal(o)).isEqualTo(0.0);
    }

    @Test
    void should_defaultOrderItemUnitPrice_when_null() {
        OrderItemDto i = new OrderItemDto("p", 1, null);
        assertThat(adapter.orderItemUnitPrice(i)).isEqualTo(0.0);
    }

    @Test
    void should_defaultPromotionDiscount_when_null() {
        PromotionDto p = new PromotionDto("c", "d", null);
        assertThat(adapter.promotionDiscount(p)).isEqualTo(0.0);
    }

    @Test
    void should_returnPageNumber_when_pageGiven() {
        PageDto<String> p = new PageDto<>(List.of(), 0L, 0, 7);
        assertThat(adapter.productPagePageNumber(p)).isEqualTo(7);
        assertThat(adapter.orderPagePageNumber(p)).isEqualTo(7);
    }
}
