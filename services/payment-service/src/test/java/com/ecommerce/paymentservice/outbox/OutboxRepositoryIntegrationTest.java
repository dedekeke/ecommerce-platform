package com.ecommerce.paymentservice.outbox;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class OutboxRepositoryIntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void should_persistAndRetrieveOutboxEvent_when_savingNewRow() {
        OutboxEvent event = newEvent("Payment", "order-1", "PAYMENT_COMPLETED");

        OutboxEvent saved = outboxRepository.save(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.isPublished()).isFalse();
    }

    @Test
    void should_returnUnpublishedInFifoOrder_when_findingUnpublished() throws Exception {
        OutboxEvent first = outboxRepository.save(newEvent("Payment", "o-1", "PAYMENT_COMPLETED"));
        Thread.sleep(5);
        OutboxEvent second = outboxRepository.save(newEvent("Payment", "o-2", "PAYMENT_FAILED"));

        List<OutboxEvent> batch = outboxRepository.findUnpublished(PageRequest.of(0, 10));

        assertThat(batch).extracting(OutboxEvent::getId)
            .containsExactly(first.getId(), second.getId());
    }

    @Test
    void should_markRowsPublished_when_invokingMarkPublished() {
        OutboxEvent a = outboxRepository.save(newEvent("Payment", "o-a", "PAYMENT_COMPLETED"));
        OutboxEvent b = outboxRepository.save(newEvent("Payment", "o-b", "PAYMENT_COMPLETED"));
        LocalDateTime now = LocalDateTime.now();

        int updated = outboxRepository.markPublished(List.of(a.getId()), now);

        // Bulk JPQL UPDATE bypasses the persistence context cache.
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(outboxRepository.findById(a.getId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(outboxRepository.findById(b.getId()).orElseThrow().getPublishedAt()).isNull();
    }

    @Test
    void should_countOnlyUnpublished_when_callingCountByPublishedAtIsNull() {
        outboxRepository.save(newEvent("Payment", "o-1", "PAYMENT_COMPLETED"));
        OutboxEvent done = newEvent("Payment", "o-2", "PAYMENT_COMPLETED");
        done.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(done);

        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1L);
    }

    private OutboxEvent newEvent(String aggregateType, String aggregateId, String eventType) {
        return OutboxEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .topic(eventType.toLowerCase().replace('_', '.'))
            .payload("{\"id\":\"" + aggregateId + "\"}")
            .attemptCount(0)
            .build();
    }
}
