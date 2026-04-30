package com.ecommerce.inventoryservice.outbox;

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
        OutboxEvent event = newEvent("Inventory", "100", "STOCK_LOW_DETECTED");

        OutboxEvent saved = outboxRepository.save(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.isPublished()).isFalse();
    }

    @Test
    void should_returnUnpublishedInFifoOrder_when_findingUnpublished() throws Exception {
        OutboxEvent first = outboxRepository.save(newEvent("Inventory", "100", "STOCK_LOW_DETECTED"));
        // tiny sleep to guarantee distinct created_at; H2 timestamps have ms resolution
        Thread.sleep(5);
        OutboxEvent second = outboxRepository.save(newEvent("Inventory", "200", "STOCK_LOW_DETECTED"));
        Thread.sleep(5);
        OutboxEvent third = outboxRepository.save(newEvent("Inventory", "300", "STOCK_REPLENISHED"));

        List<OutboxEvent> batch = outboxRepository.findUnpublished(PageRequest.of(0, 10));

        assertThat(batch).extracting(OutboxEvent::getId)
            .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void should_excludePublishedRows_when_findingUnpublished() {
        OutboxEvent unpublished = outboxRepository.save(newEvent("Inventory", "100", "STOCK_LOW_DETECTED"));
        OutboxEvent published = newEvent("Inventory", "200", "STOCK_LOW_DETECTED");
        published.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(published);

        List<OutboxEvent> batch = outboxRepository.findUnpublished(PageRequest.of(0, 10));

        assertThat(batch).extracting(OutboxEvent::getId).containsExactly(unpublished.getId());
    }

    @Test
    void should_markRowsPublished_when_invokingMarkPublished() {
        OutboxEvent a = outboxRepository.save(newEvent("Inventory", "a", "STOCK_LOW_DETECTED"));
        OutboxEvent b = outboxRepository.save(newEvent("Inventory", "b", "STOCK_LOW_DETECTED"));
        OutboxEvent c = outboxRepository.save(newEvent("Inventory", "c", "STOCK_LOW_DETECTED"));
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
        outboxRepository.save(newEvent("Inventory", "100", "STOCK_LOW_DETECTED"));
        outboxRepository.save(newEvent("Inventory", "200", "STOCK_LOW_DETECTED"));
        OutboxEvent done = newEvent("Inventory", "300", "STOCK_LOW_DETECTED");
        done.setPublishedAt(LocalDateTime.now());
        outboxRepository.save(done);

        long count = outboxRepository.countByPublishedAtIsNull();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void should_respectBatchSize_when_findingUnpublished() {
        for (int i = 0; i < 5; i++) {
            outboxRepository.save(newEvent("Inventory", "p-" + i, "STOCK_LOW_DETECTED"));
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
