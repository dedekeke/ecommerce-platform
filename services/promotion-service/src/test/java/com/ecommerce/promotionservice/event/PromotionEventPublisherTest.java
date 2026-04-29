package com.ecommerce.promotionservice.event;

import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.model.PromotionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionEventPublisherTest {

    @Mock
    private KafkaTemplate<String, PromotionEvent> kafkaTemplate;

    private PromotionEventPublisher publisher;

    private Promotion promotion;

    @BeforeEach
    void setUp() {
        publisher = new PromotionEventPublisher(kafkaTemplate);
        promotion = Promotion.builder()
                .id(1L)
                .code("SUMMER25")
                .name("Summer Sale")
                .description("25% off everything")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(25))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();
    }

    @Test
    void should_publishPromotionCreatedEvent_when_publisherInvoked() {
        publisher.publishPromotionCreated(promotion);

        ArgumentCaptor<PromotionEvent> captor = ArgumentCaptor.forClass(PromotionEvent.class);
        verify(kafkaTemplate).send(eq(PromotionEventPublisher.PROMOTION_CREATED_TOPIC),
                eq("SUMMER25"), captor.capture());

        PromotionEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("PROMOTION_CREATED");
        assertThat(event.getPromoCode()).isEqualTo("SUMMER25");
        assertThat(event.getName()).isEqualTo("Summer Sale");
        assertThat(event.getDescription()).isEqualTo("25% off everything");
        assertThat(event.getExpiresAt()).isEqualTo(promotion.getEndDate());
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void should_swallowException_when_kafkaSendFails() {
        when(kafkaTemplate.send(eq(PromotionEventPublisher.PROMOTION_CREATED_TOPIC),
                eq("SUMMER25"), any(PromotionEvent.class)))
                .thenThrow(new RuntimeException("broker down"));

        publisher.publishPromotionCreated(promotion);

        verify(kafkaTemplate).send(eq(PromotionEventPublisher.PROMOTION_CREATED_TOPIC),
                eq("SUMMER25"), any(PromotionEvent.class));
    }

    @Test
    void should_skipPublish_when_kafkaTemplateNotConfigured() {
        PromotionEventPublisher noBrokerPublisher = new PromotionEventPublisher(null);

        noBrokerPublisher.publishPromotionCreated(promotion);

        verify(kafkaTemplate, never()).send(any(), any(), any(PromotionEvent.class));
    }
}
