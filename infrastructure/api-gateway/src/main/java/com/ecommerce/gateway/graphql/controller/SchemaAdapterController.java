package com.ecommerce.gateway.graphql.controller;

import com.ecommerce.gateway.graphql.dto.CartDto;
import com.ecommerce.gateway.graphql.dto.CartItemDto;
import com.ecommerce.gateway.graphql.dto.OrderDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bridges GraphQL field names to the BFF DTO shape where they differ from a
 * straight bean-property mapping.
 *
 * <p>Spring for GraphQL's default {@code PropertyDataFetcher} handles record
 * accessors and identical names automatically — these {@link SchemaMapping}s
 * cover only the fields that need adaptation or computation.
 */
@Controller
public class SchemaAdapterController {

    // ---------- Product ------------------------------------------------------

    @SchemaMapping(typeName = "Product", field = "inStock")
    public boolean productInStock(ProductDto source) {
        return source.resolvedInStock();
    }

    @SchemaMapping(typeName = "Product", field = "liveStockQty")
    public Integer productLiveStockQty(ProductDto source) {
        // Live stock comes from inventory-service via SSE in a later iteration.
        // For now, return the snapshot stockQuantity so the UI has *something*
        // and can still degrade gracefully (the schema marks this nullable).
        return source.stockQuantity();
    }

    @SchemaMapping(typeName = "Product", field = "images")
    public List<String> productImages(ProductDto source) {
        return source.images() == null ? List.of() : source.images();
    }

    @SchemaMapping(typeName = "Product", field = "currency")
    public String productCurrency(ProductDto source) {
        return source.currency() == null ? "USD" : source.currency();
    }

    @SchemaMapping(typeName = "Product", field = "price")
    public Double productPrice(ProductDto source) {
        return source.price() == null ? 0.0 : source.price().doubleValue();
    }

    // ---------- Cart ---------------------------------------------------------

    @SchemaMapping(typeName = "Cart", field = "subtotal")
    public Double cartSubtotal(CartDto source) {
        BigDecimal v = source.totalAmount();
        return v == null ? 0.0 : v.doubleValue();
    }

    @SchemaMapping(typeName = "Cart", field = "itemCount")
    public int cartItemCount(CartDto source) {
        Integer v = source.totalItems();
        return v == null ? 0 : v;
    }

    @SchemaMapping(typeName = "Cart", field = "items")
    public List<CartItemDto> cartItems(CartDto source) {
        return source.safeItems();
    }

    @SchemaMapping(typeName = "Cart", field = "appliedPromotions")
    public List<com.ecommerce.gateway.graphql.dto.PromotionDto> cartPromotions(CartDto source) {
        return source.safePromotions();
    }

    // ---------- CartItem -----------------------------------------------------

    @SchemaMapping(typeName = "CartItem", field = "unitPrice")
    public Double cartItemUnitPrice(CartItemDto source) {
        return source.price() == null ? 0.0 : source.price().doubleValue();
    }

    @SchemaMapping(typeName = "CartItem", field = "subtotal")
    public Double cartItemSubtotal(CartItemDto source) {
        return source.subtotal() == null ? 0.0 : source.subtotal().doubleValue();
    }

    // ---------- Order --------------------------------------------------------

    @SchemaMapping(typeName = "Order", field = "items")
    public List<com.ecommerce.gateway.graphql.dto.OrderItemDto> orderItems(OrderDto source) {
        return source.safeItems();
    }

    @SchemaMapping(typeName = "Order", field = "totalAmount")
    public Double orderTotal(OrderDto source) {
        return source.totalAmount() == null ? 0.0 : source.totalAmount().doubleValue();
    }

    // ---------- OrderItem ----------------------------------------------------

    @SchemaMapping(typeName = "OrderItem", field = "unitPrice")
    public Double orderItemUnitPrice(com.ecommerce.gateway.graphql.dto.OrderItemDto source) {
        return source.unitPrice() == null ? 0.0 : source.unitPrice().doubleValue();
    }

    // ---------- Promotion ----------------------------------------------------

    @SchemaMapping(typeName = "Promotion", field = "discountAmount")
    public Double promotionDiscount(com.ecommerce.gateway.graphql.dto.PromotionDto source) {
        return source.discountAmount() == null ? 0.0 : source.discountAmount().doubleValue();
    }

    // ---------- ProductPage / OrderPage --------------------------------------
    //
    // Spring Data's PageImpl serializes the current page index as "number"
    // but the schema (matching frontend convention) calls it "pageNumber".
    // The two adapter methods below bridge that gap without forcing the BFF
    // DTO to deviate from the upstream wire format.

    @SchemaMapping(typeName = "ProductPage", field = "pageNumber")
    public int productPagePageNumber(com.ecommerce.gateway.graphql.dto.PageDto<?> source) {
        return source.number();
    }

    @SchemaMapping(typeName = "OrderPage", field = "pageNumber")
    public int orderPagePageNumber(com.ecommerce.gateway.graphql.dto.PageDto<?> source) {
        return source.number();
    }
}
