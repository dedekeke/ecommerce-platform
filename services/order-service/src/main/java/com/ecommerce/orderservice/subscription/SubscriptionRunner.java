package com.ecommerce.orderservice.subscription;

import com.ecommerce.orderservice.client.ProductPriceClient;
import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Per-subscription unit of work, isolated in its own bean so each invocation
 * runs in an independent {@code REQUIRES_NEW} transaction.
 *
 * <p>This is the fix for batch-rollback poisoning: when the scheduler processed
 * the whole batch inside one shared transaction, a single {@code createOrder}
 * failure marked that transaction rollback-only, so even though the scheduler
 * caught the exception and continued, the eventual commit threw
 * {@code UnexpectedRollbackException} and rolled back every other subscription's
 * work. Giving each subscription its own transaction means one bad row commits
 * (or rolls back) in isolation and never poisons its siblings.</p>
 *
 * <p>It lives in a separate bean (not a private method on the scheduler) because
 * {@code @Transactional} is proxy-based: a self-invocation would bypass the
 * proxy and inherit the caller's transaction, defeating {@code REQUIRES_NEW}.</p>
 */
@Slf4j
@Component
public class SubscriptionRunner {

    private final SubscriptionRepository subscriptionRepository;
    private final OrderService orderService;
    private final ProductPriceClient productPriceClient;
    private final ObjectMapper objectMapper;
    private final BigDecimal fallbackPrice;

    public SubscriptionRunner(
        SubscriptionRepository subscriptionRepository,
        OrderService orderService,
        ProductPriceClient productPriceClient,
        ObjectMapper objectMapper,
        @Value("${subscription.fallback-price:0.01}") BigDecimal fallbackPrice
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.orderService = orderService;
        this.productPriceClient = productPriceClient;
        this.objectMapper = objectMapper;
        this.fallbackPrice = fallbackPrice;
    }

    /**
     * Fire the order for one due subscription and advance its cursor, all within
     * a brand-new transaction. A failure here rolls back only this subscription;
     * {@code nextRunAt} stays unchanged so the next poll retries it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fireAndAdvance(Subscription sub, LocalDateTime now) {
        fireOrder(sub);
        advance(sub, now);
    }

    private void fireOrder(Subscription sub) {
        Address address = parseAddress(sub.getShippingAddressJson());
        BigDecimal price = productPriceClient.getCurrentPrice(sub.getProductId())
            .orElseGet(() -> {
                log.warn("No current price for product {} (subscription {}) — using fallback {}",
                    sub.getProductId(), sub.getId(), fallbackPrice);
                return fallbackPrice;
            });
        OrderItem item = OrderItem.builder()
            .productId(sub.getProductId())
            .productName("Subscription product " + sub.getProductId())
            .price(price)
            .quantity(sub.getQuantity())
            .build();
        orderService.createOrder(sub.getUserId(), List.of(item), address, null);
    }

    private Address parseAddress(String json) {
        try {
            return objectMapper.readValue(json, Address.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                "Subscription has malformed shippingAddressJson: " + ex.getMessage(), ex);
        }
    }

    private void advance(Subscription sub, LocalDateTime now) {
        sub.setLastRunAt(now);
        sub.setNextRunAt(sub.getNextRunAt().plusDays(sub.getIntervalDays()));
        subscriptionRepository.save(sub);
    }
}
