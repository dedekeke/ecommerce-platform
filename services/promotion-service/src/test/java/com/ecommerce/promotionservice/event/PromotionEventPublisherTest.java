package com.ecommerce.promotionservice.event;

import com.ecommerce.promotionservice.client.UserContact;
import com.ecommerce.promotionservice.client.UserServiceClient;
import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.model.PromotionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionEventPublisher")
class PromotionEventPublisherTest {

    @Mock
    private KafkaTemplate<String, PromotionEvent> kafkaTemplate;

    @Mock
    private PromotionUserTargetRepository targetRepository;

    @Mock
    private UserServiceClient userServiceClient;

    private PromotionEventPublisher publisher;

    private Promotion promotion;

    @BeforeEach
    void setUp() {
        publisher = new PromotionEventPublisher(kafkaTemplate, targetRepository, userServiceClient);
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
    @DisplayName("should_broadcastWithoutEmail_when_noTargets")
    void should_broadcastWithoutEmail_when_noTargets() {
        when(targetRepository.findByPromoCode("SUMMER25")).thenReturn(List.of());

        publisher.publishPromotionCreated(promotion);

        PromotionEvent event = capturedEvent();
        assertThat(event.getEventType()).isEqualTo("PROMOTION_CREATED");
        assertThat(event.getPromoCode()).isEqualTo("SUMMER25");
        assertThat(event.getName()).isEqualTo("Summer Sale");
        assertThat(event.getExpiresAt()).isEqualTo(promotion.getEndDate());
        assertThat(event.getUserEmail()).isNull();
        verifyNoInteractions(userServiceClient);
    }

    @Test
    @DisplayName("should_publishPerUserEvent_when_targetsResolve")
    void should_publishPerUserEvent_when_targetsResolve() {
        when(targetRepository.findByPromoCode("SUMMER25")).thenReturn(List.of(
                target("auth0|alice"), target("auth0|bob")));
        when(userServiceClient.findByAuth0Id("auth0|alice"))
                .thenReturn(Optional.of(new UserContact("1", "alice@example.com", "Alice A")));
        when(userServiceClient.findByAuth0Id("auth0|bob"))
                .thenReturn(Optional.of(new UserContact("2", "bob@example.com", "Bob B")));

        publisher.publishPromotionCreated(promotion);

        ArgumentCaptor<PromotionEvent> captor = ArgumentCaptor.forClass(PromotionEvent.class);
        verify(kafkaTemplate, times(2)).send(eq(PromotionEventPublisher.PROMOTION_CREATED_TOPIC),
                eq("SUMMER25"), captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PromotionEvent::getUserEmail)
                .containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
    }

    @Test
    @DisplayName("should_skipUnresolvableTarget_butStillSendResolvableOnes")
    void should_skipUnresolvableTarget_butStillSendResolvableOnes() {
        when(targetRepository.findByPromoCode("SUMMER25")).thenReturn(List.of(
                target("auth0|alice"), target("auth0|ghost")));
        when(userServiceClient.findByAuth0Id("auth0|alice"))
                .thenReturn(Optional.of(new UserContact("1", "alice@example.com", "Alice A")));
        when(userServiceClient.findByAuth0Id("auth0|ghost")).thenReturn(Optional.empty());

        publisher.publishPromotionCreated(promotion);

        PromotionEvent event = capturedEvent();
        assertThat(event.getUserEmail()).isEqualTo("alice@example.com");
        verify(kafkaTemplate, times(1)).send(any(), eq("SUMMER25"), any(PromotionEvent.class));
    }

    @Test
    @DisplayName("should_treatBlankEmailAsUnresolved_andFallBackToBroadcast")
    void should_treatBlankEmailAsUnresolved_andFallBackToBroadcast() {
        when(targetRepository.findByPromoCode("SUMMER25")).thenReturn(List.of(target("auth0|blank")));
        when(userServiceClient.findByAuth0Id("auth0|blank"))
                .thenReturn(Optional.of(new UserContact("9", "   ", "Blank")));

        publisher.publishPromotionCreated(promotion);

        assertThat(capturedEvent().getUserEmail()).isNull();
    }

    @Test
    @DisplayName("should_fallBackToBroadcast_when_allTargetsUnresolvable")
    void should_fallBackToBroadcast_when_allTargetsUnresolvable() {
        when(targetRepository.findByPromoCode("SUMMER25")).thenReturn(List.of(target("auth0|ghost")));
        when(userServiceClient.findByAuth0Id("auth0|ghost")).thenReturn(Optional.empty());

        publisher.publishPromotionCreated(promotion);

        PromotionEvent event = capturedEvent();
        assertThat(event.getUserEmail()).isNull();
        verify(kafkaTemplate, times(1)).send(any(), eq("SUMMER25"), any(PromotionEvent.class));
    }

    @Test
    @DisplayName("should_swallowException_when_kafkaSendFails")
    void should_swallowException_when_kafkaSendFails() {
        when(targetRepository.findByPromoCode("SUMMER25")).thenReturn(List.of());
        when(kafkaTemplate.send(eq(PromotionEventPublisher.PROMOTION_CREATED_TOPIC),
                eq("SUMMER25"), any(PromotionEvent.class)))
                .thenThrow(new RuntimeException("broker down"));

        publisher.publishPromotionCreated(promotion);

        verify(kafkaTemplate).send(eq(PromotionEventPublisher.PROMOTION_CREATED_TOPIC),
                eq("SUMMER25"), any(PromotionEvent.class));
    }

    @Test
    @DisplayName("should_skipPublish_when_kafkaTemplateNotConfigured")
    void should_skipPublish_when_kafkaTemplateNotConfigured() {
        PromotionEventPublisher noBrokerPublisher =
                new PromotionEventPublisher(null, targetRepository, userServiceClient);

        noBrokerPublisher.publishPromotionCreated(promotion);

        verify(kafkaTemplate, never()).send(any(), any(), any(PromotionEvent.class));
        verifyNoInteractions(targetRepository);
    }

    private PromotionEvent capturedEvent() {
        ArgumentCaptor<PromotionEvent> captor = ArgumentCaptor.forClass(PromotionEvent.class);
        verify(kafkaTemplate).send(eq(PromotionEventPublisher.PROMOTION_CREATED_TOPIC),
                eq("SUMMER25"), captor.capture());
        return captor.getValue();
    }

    private static PromotionUserTarget target(String userId) {
        return PromotionUserTarget.builder().promoCode("SUMMER25").userId(userId).build();
    }
}
