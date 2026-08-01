package com.ecommerce.reviewservice.service;

import com.ecommerce.reviewservice.dto.ReviewSummaryResponse;
import com.ecommerce.reviewservice.kafka.ReviewEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void should_publish_review_created_to_kafka_with_payload() throws Exception {
        ReviewEventPublisher publisher = new ReviewEventPublisher(kafkaTemplate, new ObjectMapper());
        ReviewSummaryResponse summary = new ReviewSummaryResponse(4.5, 2, Map.of(1, 0L, 2, 0L, 3, 0L, 4, 1L, 5, 1L));

        publisher.publishReviewCreated("p1", 5, summary);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(ReviewEventPublisher.REVIEW_CREATED_TOPIC), eq("p1"), json.capture());

        JsonNode body = new ObjectMapper().readTree(json.getValue());
        assertThat(body.get("eventType").asText()).isEqualTo("review.created");
        assertThat(body.get("productId").asText()).isEqualTo("p1");
        assertThat(body.get("rating").asInt()).isEqualTo(5);
        assertThat(body.get("summary").get("averageRating").asDouble()).isEqualTo(4.5);
    }

    @Test
    void should_swallow_kafka_exceptions_so_review_creation_is_not_blocked() {
        doThrow(new RuntimeException("kafka down"))
                .when(kafkaTemplate).send(eq(ReviewEventPublisher.REVIEW_CREATED_TOPIC), eq("p1"), org.mockito.ArgumentMatchers.anyString());

        ReviewEventPublisher publisher = new ReviewEventPublisher(kafkaTemplate, new ObjectMapper());

        // Must not throw — review creation must succeed even if Kafka is down.
        publisher.publishReviewCreated("p1", 4, new ReviewSummaryResponse(4.0, 1, Map.of()));
    }
}
