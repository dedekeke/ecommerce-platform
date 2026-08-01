package com.ecommerce.reviewservice.kafka;

import com.ecommerce.reviewservice.dto.ReviewSummaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes {@code review.created} events for downstream search-ranking signal
 * consumers (search-service updates rating-based boosts on this topic).
 *
 * <p>Failures are logged and swallowed: a Kafka outage must never block the
 * synchronous write path from completing — the review is already persisted in
 * Mongo by the time we get here, and search ranking can be reconciled out of
 * band.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEventPublisher {

    public static final String REVIEW_CREATED_TOPIC = "review.created";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishReviewCreated(String productId, int rating, ReviewSummaryResponse summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "review.created");
        payload.put("productId", productId);
        payload.put("rating", rating);
        payload.put("summary", summary);
        payload.put("timestamp", Instant.now().toString());

        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(REVIEW_CREATED_TOPIC, productId, json);
            log.debug("Published review.created for productId={}", productId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize review.created payload for productId={}", productId, e);
        } catch (RuntimeException e) {
            log.error("Failed to publish review.created for productId={}", productId, e);
        }
    }
}
