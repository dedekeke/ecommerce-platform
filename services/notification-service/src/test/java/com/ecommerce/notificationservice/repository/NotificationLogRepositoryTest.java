package com.ecommerce.notificationservice.repository;

import com.ecommerce.notificationservice.BaseMongoTest;
import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for NotificationLogRepository
 * Following TDD principles
 */
class NotificationLogRepositoryTest extends BaseMongoTest {

    @Autowired
    private NotificationLogRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldSaveNotificationLog() {
        // Given
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", "John Doe");
        variables.put("orderNumber", "ORD-001");

        NotificationLog log = NotificationLog.builder()
                .userId("user123")
                .recipient("test@example.com")
                .type(NotificationType.EMAIL)
                .templateCode("ORDER_CONFIRMATION")
                .subject("Order Confirmation")
                .body("Your order has been confirmed")
                .variables(variables)
                .status(NotificationStatus.PENDING)
                .relatedEntityId("order123")
                .relatedEntityType("ORDER")
                .retryCount(0)
                .build();

        // When
        NotificationLog saved = repository.save(log);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo("user123");
        assertThat(saved.getRecipient()).isEqualTo("test@example.com");
        assertThat(saved.getType()).isEqualTo(NotificationType.EMAIL);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(0);
    }

    @Test
    void shouldFindByUserId() {
        // Given
        NotificationLog log1 = createNotificationLog("user123", "test1@example.com");
        NotificationLog log2 = createNotificationLog("user123", "test2@example.com");
        NotificationLog log3 = createNotificationLog("user456", "test3@example.com");

        repository.saveAll(List.of(log1, log2, log3));

        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<NotificationLog> result = repository.findByUserId("user123", pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .allMatch(log -> log.getUserId().equals("user123"));
    }

    @Test
    void shouldFindByStatusAndNextRetryAtBefore() {
        // Given
        Instant now = Instant.now();

        NotificationLog retryableLog = createNotificationLog("user1", "test1@example.com");
        retryableLog.setStatus(NotificationStatus.RETRYING);
        retryableLog.setNextRetryAt(now.minus(5, ChronoUnit.MINUTES));

        NotificationLog futureRetryLog = createNotificationLog("user2", "test2@example.com");
        futureRetryLog.setStatus(NotificationStatus.RETRYING);
        futureRetryLog.setNextRetryAt(now.plus(5, ChronoUnit.MINUTES));

        NotificationLog sentLog = createNotificationLog("user3", "test3@example.com");
        sentLog.setStatus(NotificationStatus.SENT);

        repository.saveAll(List.of(retryableLog, futureRetryLog, sentLog));

        // When
        List<NotificationLog> result = repository.findByStatusAndNextRetryAtBefore(
                NotificationStatus.RETRYING,
                now
        );

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo("user1");
        assertThat(result.get(0).getStatus()).isEqualTo(NotificationStatus.RETRYING);
    }

    @Test
    void shouldFindByRelatedEntityIdAndRelatedEntityType() {
        // Given
        NotificationLog orderLog1 = createNotificationLog("user1", "test1@example.com");
        orderLog1.setRelatedEntityId("order123");
        orderLog1.setRelatedEntityType("ORDER");

        NotificationLog orderLog2 = createNotificationLog("user2", "test2@example.com");
        orderLog2.setRelatedEntityId("order123");
        orderLog2.setRelatedEntityType("ORDER");

        NotificationLog paymentLog = createNotificationLog("user3", "test3@example.com");
        paymentLog.setRelatedEntityId("payment456");
        paymentLog.setRelatedEntityType("PAYMENT");

        repository.saveAll(List.of(orderLog1, orderLog2, paymentLog));

        // When
        List<NotificationLog> result = repository.findByRelatedEntityIdAndRelatedEntityType(
                "order123",
                "ORDER"
        );

        // Then
        assertThat(result).hasSize(2);
        assertThat(result)
                .allMatch(log -> log.getRelatedEntityId().equals("order123") &&
                                 log.getRelatedEntityType().equals("ORDER"));
    }

    @Test
    void shouldUpdateNotificationStatus() {
        // Given
        NotificationLog log = createNotificationLog("user123", "test@example.com");
        log.setStatus(NotificationStatus.PENDING);
        NotificationLog saved = repository.save(log);

        // When
        saved.setStatus(NotificationStatus.SENT);
        saved.setSentAt(Instant.now());
        NotificationLog updated = repository.save(saved);

        // Then
        assertThat(updated.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(updated.getSentAt()).isNotNull();
    }

    @Test
    void shouldIncrementRetryCount() {
        // Given
        NotificationLog log = createNotificationLog("user123", "test@example.com");
        log.setRetryCount(0);
        log.setStatus(NotificationStatus.FAILED);
        NotificationLog saved = repository.save(log);

        // When
        saved.setRetryCount(saved.getRetryCount() + 1);
        saved.setStatus(NotificationStatus.RETRYING);
        saved.setNextRetryAt(Instant.now().plus(2, ChronoUnit.MINUTES));
        NotificationLog updated = repository.save(saved);

        // Then
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(updated.getNextRetryAt()).isNotNull();
    }

    private NotificationLog createNotificationLog(String userId, String recipient) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", "Test User");

        return NotificationLog.builder()
                .userId(userId)
                .recipient(recipient)
                .type(NotificationType.EMAIL)
                .templateCode("TEST_TEMPLATE")
                .subject("Test Subject")
                .body("Test Body")
                .variables(variables)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();
    }
}
