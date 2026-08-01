package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test against embedded H2 for the admin-list repository queries:
 * the paged {@code findByStatus(status, pageable)} status filter and the paged
 * {@code findAll(pageable)} used for the unfiltered admin list.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class OrderRepositoryAdminListIntegrationTest {

    @Autowired
    private OrderRepository repository;

    @Test
    void should_returnOnlyMatchingStatus_when_findByStatusPaged() {
        repository.save(order("ORD-1", "user-1", OrderStatus.PENDING));
        repository.save(order("ORD-2", "user-2", OrderStatus.SHIPPED));
        repository.save(order("ORD-3", "user-3", OrderStatus.SHIPPED));

        Page<Order> shipped = repository.findByStatus(
            OrderStatus.SHIPPED, PageRequest.of(0, 10, sortByCreatedDesc()));

        assertThat(shipped.getTotalElements()).isEqualTo(2);
        assertThat(shipped.getContent())
            .allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.SHIPPED));
    }

    @Test
    void should_paginate_when_findByStatusPaged() {
        for (int i = 0; i < 5; i++) {
            repository.save(order("ORD-P-" + i, "user-" + i, OrderStatus.PENDING));
        }

        Page<Order> firstPage = repository.findByStatus(
            OrderStatus.PENDING, PageRequest.of(0, 2, sortByCreatedDesc()));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getNumberOfElements()).isEqualTo(2);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();
    }

    @Test
    void should_returnAllOrdersPaged_when_findAll() {
        repository.save(order("ORD-A", "user-1", OrderStatus.PENDING));
        repository.save(order("ORD-B", "user-2", OrderStatus.SHIPPED));
        repository.save(order("ORD-C", "user-3", OrderStatus.DELIVERED));

        Page<Order> page = repository.findAll(PageRequest.of(0, 10, sortByCreatedDesc()));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    private static Sort sortByCreatedDesc() {
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private static Order order(String orderNumber, String userId, OrderStatus status) {
        OrderItem item = OrderItem.builder()
            .productId("p-1").productName("Widget")
            .price(new BigDecimal("50.00")).quantity(1)
            .subtotal(new BigDecimal("50.00"))
            .build();
        Order order = Order.builder()
            .orderNumber(orderNumber)
            .userId(userId)
            .status(status)
            .subtotal(new BigDecimal("50.00"))
            .tax(new BigDecimal("4.00"))
            .shippingCost(new BigDecimal("5.99"))
            .total(new BigDecimal("59.99"))
            .shippingAddress(Address.builder()
                .street("1 Main St").city("SF").state("CA")
                .postalCode("94105").country("USA").build())
            .items(List.of(item))
            .build();
        item.setOrder(order);
        return order;
    }
}
