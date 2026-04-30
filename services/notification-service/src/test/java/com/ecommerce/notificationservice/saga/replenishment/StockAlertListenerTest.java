package com.ecommerce.notificationservice.saga.replenishment;

import com.ecommerce.notificationservice.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockAlertListener — choreography participant")
class StockAlertListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private ConsumedReplenishmentEventRepository consumedRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private StockAlertListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new StockAlertListener(emailService, consumedRepository, kafkaTemplate, objectMapper);
        ReflectionTestUtils.setField(listener, "adminEmail", "admin@ecommerce.com");
    }

    @Test
    @DisplayName("should email admin and emit admin.notified on stock.low.detected")
    void should_emailAdmin_when_stockLowReceived() throws Exception {
        when(consumedRepository.existsById(any())).thenReturn(false);

        StockLowDetectedEvent event = new StockLowDetectedEvent(
                UUID.randomUUID(), 100L, "SKU-100", 2, 5, Instant.now());
        listener.onStockLow(objectMapper.writeValueAsString(event));

        verify(emailService).sendEmail(eq("admin@ecommerce.com"), anyString(), eq("stock-low-alert"), any(Map.class));
        verify(consumedRepository).save(any(ConsumedReplenishmentEvent.class));
        verify(kafkaTemplate).send(eq("admin.notified.due-to-stock"), anyString(), any(AdminNotifiedEvent.class));
    }

    @Test
    @DisplayName("should email admin and emit admin.stock-back on stock.replenished")
    void should_emailAdmin_when_stockReplenishedReceived() throws Exception {
        when(consumedRepository.existsById(any())).thenReturn(false);

        StockReplenishedEvent event = new StockReplenishedEvent(
                UUID.randomUUID(), 100L, "SKU-100", 50, Instant.now());
        listener.onStockReplenished(objectMapper.writeValueAsString(event));

        verify(emailService).sendEmail(eq("admin@ecommerce.com"), anyString(), eq("stock-back-alert"), any(Map.class));
        verify(kafkaTemplate).send(eq("admin.stock-back.due-to-stock"), anyString(), any(StockBackInStockEvent.class));
    }

    @Test
    @DisplayName("should drop duplicate stock.low.detected events (idempotency)")
    void should_dropDuplicate_when_eventAlreadyConsumed() throws Exception {
        when(consumedRepository.existsById(any())).thenReturn(true);

        StockLowDetectedEvent event = new StockLowDetectedEvent(
                UUID.randomUUID(), 100L, "SKU-100", 2, 5, Instant.now());
        listener.onStockLow(objectMapper.writeValueAsString(event));

        verifyNoInteractions(emailService);
        verifyNoInteractions(kafkaTemplate);
        verify(consumedRepository, never()).save(any());
    }

    @Test
    @DisplayName("should swallow malformed payload to avoid blocking the partition")
    void should_swallowError_when_messageMalformed() {
        listener.onStockLow("not json");
        verifyNoInteractions(emailService);
        verifyNoInteractions(kafkaTemplate);
    }
}
