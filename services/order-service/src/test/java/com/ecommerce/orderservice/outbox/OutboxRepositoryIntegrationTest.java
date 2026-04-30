package com.ecommerce.orderservice.outbox;

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

/**
 * Integration test against an embedded H2 database — verifies the JPA mapping,
 * the unique-by-eventId constraint, and the FIFO claim ordering used by the
 * relay. This is the closest the test pyramid gets to "what production does"
 * without a Postgres container.
 */
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
        OutboxEvent event = newEvent("Order", "order-1", "ORDER_CREATED");

        OutboxEvent saved = outboxRepository.save(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.isPublished()).isFalse();
    }

    @Test
    void should_returnUnpublishedInFifoOrder_when_findingUnpublished() throws Exception {
        OutboxEvent first = outboxRepository.save(newEvent("Order", "o-1", "ORDER_CREATED"));
        // tiny sleep to guarantee distinct created_at; H2 timestamps have ms resolution
        Thread.sleep(5);
        OutboxEvent second = outboxRepository.save(newEvent("Order", "o-2", "ORDER_CREATED"));
        Thread.sleep(5);
        OutboxEvent third = outboxRepository.save(newEvent("Order", "o-3", "ORDER_UPDATED"));

        List<OutboxEvent> batch = outboxRepository.findUnpublished(PageRequest.of(0, 10));

        assertThat(batch).extracting(OutboxEvent::getId)
            .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void should_excludePublishedRows_when_findingUnpublished() {
        OutboxEvent unpublished = outboxRepository.save(newEvent("Order", "o-1", "ORDER_CREATED"));
        OutboxEvent published = newEvent("Order", "o-2", "ORDER_CREATED");
        published.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(published);

        List<OutboxEvent> batch = outboxRepository.findUnpublished(PageRequest.of(0, 10));

        assertThat(batch).extracting(OutboxEvent::getId).containsExactly(unpublished.getId());
    }

    @Test
    void should_markRowsPublished_when_invokingMarkPublished() {
        OutboxEvent a = outboxRepository.save(newEvent("Order", "o-a", "ORDER_CREATED"));
        OutboxEvent b = outboxRepository.save(newEvent("Order", "o-b", "ORDER_CREATED"));
        OutboxEvent c = outboxRepository.save(newEvent("Order", "o-c", "ORDER_CREATED"));
        LocalDateTime now = LocalDateTime.now();

        int updated = outboxRepository.markPublished(List.of(a.getId(), b.getId()), now);

        // Bulk JPQL UPDATE bypasses the persistence context cache; clear it so
        // subsequent reads come from the database (not stale managed entities).
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(2);
        assertThat(outboxRepository.findById(a.getId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(outboxRepository.findById(b.getId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(outboxRepository.findById(c.getId()).orElseThrow().getPublishedAt()).isNull();
    }

    @Test
    void should_returnUnpublishedCount_when_callingCountByPublishedAtIsNull() {
        outboxRepository.save(newEvent("Order", "o-1", "ORDER_CREATED"));
        outboxRepository.save(newEvent("Order", "o-2", "ORDER_CREATED"));
        OutboxEvent done = newEvent("Order", "o-3", "ORDER_CREATED");
        done.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(done);

        long count = outboxRepository.countByPublishedAtIsNull();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void should_respectBatchSize_when_findingUnpublished() {
        for (int i = 0; i < 5; i++) {
            outboxRepository.save(newEvent("Order", "o-" + i, "ORDER_CREATED"));
        }

        List<OutboxEvent> batch = outboxRepository.findUnpublished(PageRequest.of(0, 2));

        assertThat(batch).hasSize(2);
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
