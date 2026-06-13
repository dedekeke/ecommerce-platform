package com.ecommerce.promotionservice.event;

import com.ecommerce.promotionservice.client.UserContact;
import com.ecommerce.promotionservice.client.UserServiceClient;
import com.ecommerce.promotionservice.model.Promotion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Publishes promotion lifecycle events to Kafka.
 *
 * <p>Targeting (§3.x): if the promotion has one or more {@link PromotionUserTarget}
 * rows, a per-user event is published for each resolvable recipient (email +
 * name looked up from user-service). When there are no targets — or none can be
 * resolved — the publisher falls back to a single broadcast event with no
 * {@code userEmail}, which notification-service routes to the configured
 * announcement recipient.
 *
 * <p>Failures are logged but never propagated — the API call must succeed even
 * if the broker or user-service is unreachable.
 */
@Slf4j
@Component
public class PromotionEventPublisher {

    public static final String PROMOTION_CREATED_TOPIC = "promotion.created";

    private final KafkaTemplate<String, PromotionEvent> kafkaTemplate;
    private final PromotionUserTargetRepository targetRepository;
    private final UserServiceClient userServiceClient;

    public PromotionEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, PromotionEvent> kafkaTemplate,
            PromotionUserTargetRepository targetRepository,
            UserServiceClient userServiceClient) {
        this.kafkaTemplate = kafkaTemplate;
        this.targetRepository = targetRepository;
        this.userServiceClient = userServiceClient;
    }

    public void publishPromotionCreated(Promotion promotion) {
        if (kafkaTemplate == null) {
            log.debug("KafkaTemplate not configured; skipping promotion.created publish for {}",
                    promotion.getCode());
            return;
        }

        List<PromotionUserTarget> targets = targetRepository.findByPromoCode(promotion.getCode());
        if (targets.isEmpty()) {
            publishBroadcast(promotion);
            return;
        }

        int sent = 0;
        for (PromotionUserTarget target : targets) {
            sent += publishToTarget(promotion, target) ? 1 : 0;
        }

        // Every target failed to resolve — still announce so the promotion isn't silent.
        if (sent == 0) {
            log.warn("No targets resolved for promotion {}; falling back to broadcast", promotion.getCode());
            publishBroadcast(promotion);
        }
    }

    private boolean publishToTarget(Promotion promotion, PromotionUserTarget target) {
        UserContact contact = userServiceClient.findByAuth0Id(target.getUserId()).orElse(null);
        if (contact == null || contact.email() == null || contact.email().isBlank()) {
            log.warn("Could not resolve email for targeted user {} on promotion {}",
                    target.getUserId(), promotion.getCode());
            return false;
        }

        PromotionEvent event = baseEvent(promotion)
                .userId(target.getUserId())
                .userEmail(contact.email())
                .userName(contact.fullName())
                .build();
        send(promotion.getCode(), event, "targeted user " + target.getUserId());
        return true;
    }

    private void publishBroadcast(Promotion promotion) {
        send(promotion.getCode(), baseEvent(promotion).build(), "broadcast");
    }

    private PromotionEvent.PromotionEventBuilder baseEvent(Promotion promotion) {
        return PromotionEvent.builder()
                .eventType("PROMOTION_CREATED")
                .timestamp(LocalDateTime.now())
                .promoCode(promotion.getCode())
                .name(promotion.getName())
                .description(promotion.getDescription())
                .expiresAt(promotion.getEndDate());
    }

    private void send(String code, PromotionEvent event, String audience) {
        try {
            kafkaTemplate.send(PROMOTION_CREATED_TOPIC, code, event);
            log.info("Published PROMOTION_CREATED event for code: {} ({})", code, audience);
        } catch (Exception e) {
            log.error("Failed to publish PROMOTION_CREATED for code: {} ({})", code, audience, e);
        }
    }
}
