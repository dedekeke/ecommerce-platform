package com.ecommerce.paymentservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(outboxRepository, new ObjectMapper());
    }

    @Test
    void should_persistOutboxRow_when_recordingEvent() {
        when(outboxRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> payload = Map.of("orderId", "order-1", "amount", 99);

        OutboxEvent saved = outboxService.recordEvent(
            "Payment", "order-1", "PAYMENT_COMPLETED", "payment.completed", payload);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent persisted = captor.getValue();
        assertThat(persisted.getAggregateType()).isEqualTo("Payment");
        assertThat(persisted.getAggregateId()).isEqualTo("order-1");
        assertThat(persisted.getEventType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(persisted.getTopic()).isEqualTo("payment.completed");
        assertThat(persisted.getPayload())
            .contains("\"orderId\":\"order-1\"")
            .contains("\"amount\":99");
        assertThat(persisted.getEventId()).isNotBlank();
        assertThat(persisted.getPublishedAt()).isNull();
        assertThat(persisted.getAttemptCount()).isZero();
        assertThat(saved).isSameAs(persisted);
    }

    @Test
    void should_generateUniqueEventId_when_recordingMultipleEvents() {
        when(outboxRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent first = outboxService.recordEvent(
            "Payment", "order-a", "PAYMENT_COMPLETED", "payment.completed", Map.of("k", "v"));
        OutboxEvent second = outboxService.recordEvent(
            "Payment", "order-b", "PAYMENT_FAILED", "payment.failed", Map.of("k", "v"));

        assertThat(first.getEventId()).isNotEqualTo(second.getEventId());
    }

    @Test
    void should_throwSerialisationException_when_payloadCannotBeSerialised() {
        ObjectMapper failing = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("boom") {};
            }
        };
        OutboxService bad = new OutboxService(outboxRepository, failing);

        assertThatThrownBy(() -> bad.recordEvent(
                "Payment", "order-x", "PAYMENT_COMPLETED", "payment.completed", Map.of()))
            .isInstanceOf(OutboxService.OutboxSerializationException.class);

        verify(outboxRepository, never()).save(any());
    }
}
