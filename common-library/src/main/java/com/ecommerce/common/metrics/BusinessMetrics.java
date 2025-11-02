package com.ecommerce.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * Business metrics for tracking KPIs across microservices.
 * Provides convenient methods for common business operations.
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    // Order Metrics
    private final Counter ordersCreated;
    private final Counter ordersCompleted;
    private final Counter ordersCancelled;
    private final DistributionSummary orderValue;
    private final Timer orderProcessingTime;

    // Payment Metrics
    private final Counter paymentsSuccessful;
    private final Counter paymentsFailed;
    private final DistributionSummary paymentAmount;
    private final Timer paymentProcessingTime;

    // Cart Metrics
    private final Counter cartsCreated;
    private final Counter cartsAbandoned;
    private final Counter cartItemsAdded;
    private final Counter cartItemsRemoved;

    // Inventory Metrics
    private final Counter inventoryReservations;
    private final Counter inventoryReleases;
    private final Counter stockoutEvents;

    // Product Metrics
    private final Counter productsViewed;
    private final Counter productsSearched;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Initialize Order Metrics
        this.ordersCreated = Counter.builder("business.orders.created")
                .description("Total number of orders created")
                .register(registry);

        this.ordersCompleted = Counter.builder("business.orders.completed")
                .description("Total number of orders completed")
                .register(registry);

        this.ordersCancelled = Counter.builder("business.orders.cancelled")
                .description("Total number of orders cancelled")
                .register(registry);

        this.orderValue = DistributionSummary.builder("business.orders.value")
                .description("Distribution of order values")
                .baseUnit("currency")
                .register(registry);

        this.orderProcessingTime = Timer.builder("business.orders.processing.time")
                .description("Time to process an order from creation to completion")
                .register(registry);

        // Initialize Payment Metrics
        this.paymentsSuccessful = Counter.builder("business.payments.successful")
                .description("Total number of successful payments")
                .register(registry);

        this.paymentsFailed = Counter.builder("business.payments.failed")
                .description("Total number of failed payments")
                .register(registry);

        this.paymentAmount = DistributionSummary.builder("business.payments.amount")
                .description("Distribution of payment amounts")
                .baseUnit("currency")
                .register(registry);

        this.paymentProcessingTime = Timer.builder("business.payments.processing.time")
                .description("Time to process a payment")
                .register(registry);

        // Initialize Cart Metrics
        this.cartsCreated = Counter.builder("business.carts.created")
                .description("Total number of carts created")
                .register(registry);

        this.cartsAbandoned = Counter.builder("business.carts.abandoned")
                .description("Total number of abandoned carts")
                .register(registry);

        this.cartItemsAdded = Counter.builder("business.carts.items.added")
                .description("Total number of items added to carts")
                .register(registry);

        this.cartItemsRemoved = Counter.builder("business.carts.items.removed")
                .description("Total number of items removed from carts")
                .register(registry);

        // Initialize Inventory Metrics
        this.inventoryReservations = Counter.builder("business.inventory.reservations")
                .description("Total number of inventory reservations")
                .register(registry);

        this.inventoryReleases = Counter.builder("business.inventory.releases")
                .description("Total number of inventory releases")
                .register(registry);

        this.stockoutEvents = Counter.builder("business.inventory.stockouts")
                .description("Total number of stockout events")
                .register(registry);

        // Initialize Product Metrics
        this.productsViewed = Counter.builder("business.products.viewed")
                .description("Total number of product views")
                .register(registry);

        this.productsSearched = Counter.builder("business.products.searched")
                .description("Total number of product searches")
                .register(registry);
    }

    // ========== Order Methods ==========

    public void recordOrderCreated(BigDecimal orderValue) {
        ordersCreated.increment();
        this.orderValue.record(orderValue.doubleValue());
    }

    public void recordOrderCompleted() {
        ordersCompleted.increment();
    }

    public void recordOrderCancelled(String reason) {
        Counter.builder("business.orders.cancelled")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    public Timer.Sample startOrderProcessing() {
        return Timer.start(registry);
    }

    public void recordOrderProcessingTime(Timer.Sample sample) {
        sample.stop(orderProcessingTime);
    }

    // ========== Payment Methods ==========

    public void recordPaymentSuccessful(BigDecimal amount, String paymentMethod) {
        paymentsSuccessful.increment();
        paymentAmount.record(amount.doubleValue());

        Counter.builder("business.payments.successful")
                .tag("method", paymentMethod)
                .register(registry)
                .increment();
    }

    public void recordPaymentFailed(String reason, String paymentMethod) {
        paymentsFailed.increment();

        Counter.builder("business.payments.failed")
                .tag("reason", reason)
                .tag("method", paymentMethod)
                .register(registry)
                .increment();
    }

    public Timer.Sample startPaymentProcessing() {
        return Timer.start(registry);
    }

    public void recordPaymentProcessingTime(Timer.Sample sample) {
        sample.stop(paymentProcessingTime);
    }

    public double getPaymentSuccessRate() {
        double total = paymentsSuccessful.count() + paymentsFailed.count();
        return total > 0 ? (paymentsSuccessful.count() / total) * 100 : 0;
    }

    // ========== Cart Methods ==========

    public void recordCartCreated() {
        cartsCreated.increment();
    }

    public void recordCartAbandoned() {
        cartsAbandoned.increment();
    }

    public void recordCartItemAdded(String productId) {
        cartItemsAdded.increment();

        Counter.builder("business.carts.items.added")
                .tag("product", productId)
                .register(registry)
                .increment();
    }

    public void recordCartItemRemoved(String productId) {
        cartItemsRemoved.increment();

        Counter.builder("business.carts.items.removed")
                .tag("product", productId)
                .register(registry)
                .increment();
    }

    public double getCartAbandonmentRate() {
        double total = cartsCreated.count();
        return total > 0 ? (cartsAbandoned.count() / total) * 100 : 0;
    }

    // ========== Inventory Methods ==========

    public void recordInventoryReservation(String productId, int quantity) {
        inventoryReservations.increment();

        DistributionSummary.builder("business.inventory.reservation.quantity")
                .tag("product", productId)
                .register(registry)
                .record(quantity);
    }

    public void recordInventoryRelease(String productId, int quantity) {
        inventoryReleases.increment();

        DistributionSummary.builder("business.inventory.release.quantity")
                .tag("product", productId)
                .register(registry)
                .record(quantity);
    }

    public void recordStockout(String productId) {
        stockoutEvents.increment();

        Counter.builder("business.inventory.stockouts")
                .tag("product", productId)
                .register(registry)
                .increment();
    }

    public void recordCurrentStock(String productId, int quantity) {
        registry.gauge("business.inventory.current.level",
                io.micrometer.core.instrument.Tags.of("product", productId),
                quantity);
    }

    // ========== Product Methods ==========

    public void recordProductView(String productId, String category) {
        productsViewed.increment();

        Counter.builder("business.products.viewed")
                .tag("product", productId)
                .tag("category", category)
                .register(registry)
                .increment();
    }

    public void recordProductSearch(String searchTerm, int resultsCount) {
        productsSearched.increment();

        DistributionSummary.builder("business.products.search.results")
                .tag("term", searchTerm.length() > 20 ? searchTerm.substring(0, 20) : searchTerm)
                .register(registry)
                .record(resultsCount);
    }

    // ========== Custom Metrics ==========

    /**
     * Record a custom counter metric.
     */
    public void incrementCounter(String name, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(registry)
                .increment();
    }

    /**
     * Record a custom gauge metric.
     */
    public void recordGauge(String name, Number value, String... tags) {
        registry.gauge(name,
                io.micrometer.core.instrument.Tags.of(tags),
                value);
    }

    /**
     * Record a custom timer metric.
     */
    public void recordTimer(String name, long duration, TimeUnit unit, String... tags) {
        Timer.builder(name)
                .tags(tags)
                .register(registry)
                .record(duration, unit);
    }

    /**
     * Record a custom distribution summary.
     */
    public void recordDistribution(String name, double value, String... tags) {
        DistributionSummary.builder(name)
                .tags(tags)
                .register(registry)
                .record(value);
    }
}
