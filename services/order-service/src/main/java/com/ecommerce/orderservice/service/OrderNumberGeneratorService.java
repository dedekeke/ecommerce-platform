package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for generating unique order numbers
 * Format: {PREFIX}-{YEAR}-{SEQUENCE}
 * Example: ORD-2025-00001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNumberGeneratorService {

    private final OrderRepository orderRepository;
    private final AtomicInteger counter = new AtomicInteger(0);

    @Value("${order.number.prefix:ORD}")
    private String prefix;

    @Value("${order.number.year-format:yyyy}")
    private String yearFormat;

    /**
     * Generate a unique order number
     */
    public synchronized String generateOrderNumber() {
        String year = LocalDateTime.now().format(DateTimeFormatter.ofPattern(yearFormat));

        // Get the next sequence number
        int sequence = getNextSequence(year);

        // Format: ORD-2025-00001
        String orderNumber = String.format("%s-%s-%05d", prefix, year, sequence);

        log.debug("Generated order number: {}", orderNumber);
        return orderNumber;
    }

    /**
     * Get the next sequence number for the current year
     */
    private int getNextSequence(String year) {
        // If counter is 0, initialize from database
        if (counter.get() == 0) {
            initializeCounter(year);
        }

        return counter.incrementAndGet();
    }

    /**
     * Initialize counter from the latest order number in database
     */
    private void initializeCounter(String year) {
        orderRepository.findLatestOrderNumber()
            .ifPresent(latestOrderNumber -> {
                try {
                    // Parse the sequence from format: ORD-2025-00001
                    String[] parts = latestOrderNumber.split("-");
                    if (parts.length == 3) {
                        String orderYear = parts[1];
                        // Only use the sequence if it's from the same year
                        if (orderYear.equals(year)) {
                            int lastSequence = Integer.parseInt(parts[2]);
                            counter.set(lastSequence);
                            log.info("Initialized order counter to {} for year {}", lastSequence, year);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse latest order number: {}", latestOrderNumber, e);
                }
            });
    }
}
