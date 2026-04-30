package com.ecommerce.promotionservice.saga.replenishment;

import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.model.PromotionType;
import com.ecommerce.promotionservice.repository.PromotionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionStockListener — choreography participant")
class PromotionStockListenerTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private ConsumedReplenishmentEventRepository consumedRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private PromotionStockListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new PromotionStockListener(
                promotionRepository, consumedRepository, kafkaTemplate, objectMapper);
    }

    private Promotion activePromotion(long id) {
        return Promotion.builder()
                .id(id)
                .code("PROMO-" + id)
                .name("Promo " + id)
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .active(true)
                .currentUses(0)
                .build();
    }

    @Test
    @DisplayName("should pause active promotions and emit promotion.paused.due-to-stock when stock.low.detected arrives")
    void should_pausePromotions_when_stockLowReceived() throws Exception {
        Promotion p = activePromotion(1L);
        when(promotionRepository.findByActiveTrue()).thenReturn(List.of(p));
        when(consumedRepository.existsById(any())).thenReturn(false);

        StockLowDetectedEvent event = new StockLowDetectedEvent(
                UUID.randomUUID(), 100L, "SKU-100", 2, 5, Instant.now());
        listener.onStockLow(objectMapper.writeValueAsString(event));

        assertThat(p.getActive()).isFalse();
        verify(promotionRepository).saveAll(List.of(p));
        verify(consumedRepository).save(any(ConsumedReplenishmentEvent.class));

        ArgumentCaptor<Object> kafkaPayload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("promotion.paused.due-to-stock"), anyString(), kafkaPayload.capture());
        assertThat(kafkaPayload.getValue()).isInstanceOf(PromotionPausedEvent.class);
        PromotionPausedEvent emitted = (PromotionPausedEvent) kafkaPayload.getValue();
        assertThat(emitted.causedByEventId()).isEqualTo(event.eventId());
        assertThat(emitted.pausedPromotionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should drop duplicate stock.low.detected events (idempotency)")
    void should_dropDuplicate_when_eventAlreadyConsumed() throws Exception {
        when(consumedRepository.existsById(any())).thenReturn(true);

        StockLowDetectedEvent event = new StockLowDetectedEvent(
                UUID.randomUUID(), 100L, "SKU-100", 2, 5, Instant.now());
        listener.onStockLow(objectMapper.writeValueAsString(event));

        verifyNoInteractions(promotionRepository);
        verifyNoInteractions(kafkaTemplate);
        verify(consumedRepository, never()).save(any());
    }

    @Test
    @DisplayName("should resume paused promotions and emit promotion.resumed.due-to-stock when stock.replenished arrives")
    void should_resumePromotions_when_stockReplenishedReceived() throws Exception {
        Promotion paused = activePromotion(2L);
        paused.setActive(false);
        when(promotionRepository.findAll()).thenReturn(List.of(paused));
        when(consumedRepository.existsById(any())).thenReturn(false);

        StockReplenishedEvent event = new StockReplenishedEvent(
                UUID.randomUUID(), 100L, "SKU-100", 50, Instant.now());
        listener.onStockReplenished(objectMapper.writeValueAsString(event));

        assertThat(paused.getActive()).isTrue();
        verify(promotionRepository).saveAll(List.of(paused));
        verify(kafkaTemplate).send(eq("promotion.resumed.due-to-stock"), anyString(), any(PromotionResumedEvent.class));
    }

    @Test
    @DisplayName("should swallow malformed JSON to avoid poisoning the partition")
    void should_swallowError_when_messageIsMalformed() {
        listener.onStockLow("{not valid json");
        verifyNoInteractions(promotionRepository);
        verifyNoInteractions(kafkaTemplate);
    }
}
