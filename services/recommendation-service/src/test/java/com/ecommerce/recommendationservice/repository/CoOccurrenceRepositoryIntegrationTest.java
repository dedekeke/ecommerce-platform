package com.ecommerce.recommendationservice.repository;

import com.ecommerce.recommendationservice.domain.CoOccurrenceDocument;
import com.ecommerce.recommendationservice.domain.UserPurchaseDocument;
import com.ecommerce.recommendationservice.service.CoOccurrenceUpdater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spins up a real Mongo via Testcontainers and runs the full ingest path
 * (upserts + idempotency claim). Verifies:
 *  <ul>
 *    <li>directed pair documents end up in the collection,</li>
 *    <li>{@code $inc} accumulates correctly across multiple orders,</li>
 *    <li>the {@code product_count_desc} index returns rows in count-desc order,</li>
 *    <li>orderId-level idempotency holds across re-deliveries.</li>
 *  </ul>
 */
@DataMongoTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@Import(CoOccurrenceUpdater.class)
@ExtendWith({})
@Testcontainers
class CoOccurrenceRepositoryIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7-jammy")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private CoOccurrenceUpdater updater;

    @Autowired
    private CoOccurrenceRepository coOccurrenceRepository;

    @Autowired
    private UserPurchaseRepository userPurchaseRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void should_persist_six_pair_documents_for_three_item_order() {
        coOccurrenceRepository.deleteAll();
        userPurchaseRepository.deleteAll();
        mongoTemplate.getCollection("consumed_orders").drop();

        boolean ingested = updater.ingestOrder("ord-A", "user-A", List.of("p1", "p2", "p3"));

        assertThat(ingested).isTrue();
        assertThat(coOccurrenceRepository.count()).isEqualTo(6);

        Optional<CoOccurrenceDocument> p1p2 = coOccurrenceRepository
                .findById(CoOccurrenceDocument.buildId("p1", "p2"));
        assertThat(p1p2).isPresent();
        assertThat(p1p2.get().getCount()).isEqualTo(1);

        Optional<UserPurchaseDocument> profile = userPurchaseRepository.findById("user-A");
        assertThat(profile).isPresent();
        assertThat(profile.get().getProductIds()).containsExactlyInAnyOrder("p1", "p2", "p3");
    }

    @Test
    void should_increment_existing_pair_count_when_two_orders_share_pair() {
        coOccurrenceRepository.deleteAll();
        userPurchaseRepository.deleteAll();
        mongoTemplate.getCollection("consumed_orders").drop();

        updater.ingestOrder("ord-B1", "user-B", List.of("p1", "p2"));
        updater.ingestOrder("ord-B2", "user-B", List.of("p1", "p2", "p3"));

        Optional<CoOccurrenceDocument> p1p2 = coOccurrenceRepository
                .findById(CoOccurrenceDocument.buildId("p1", "p2"));
        Optional<CoOccurrenceDocument> p1p3 = coOccurrenceRepository
                .findById(CoOccurrenceDocument.buildId("p1", "p3"));

        assertThat(p1p2).isPresent();
        assertThat(p1p2.get().getCount()).isEqualTo(2);
        assertThat(p1p3).isPresent();
        assertThat(p1p3.get().getCount()).isEqualTo(1);
    }

    @Test
    void should_be_idempotent_for_duplicate_orderId() {
        coOccurrenceRepository.deleteAll();
        userPurchaseRepository.deleteAll();
        mongoTemplate.getCollection("consumed_orders").drop();

        boolean first = updater.ingestOrder("ord-C", "user-C", List.of("p1", "p2", "p3"));
        boolean second = updater.ingestOrder("ord-C", "user-C", List.of("p1", "p2", "p3"));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(coOccurrenceRepository.count()).isEqualTo(6);
        Optional<CoOccurrenceDocument> p1p2 = coOccurrenceRepository
                .findById(CoOccurrenceDocument.buildId("p1", "p2"));
        assertThat(p1p2).isPresent();
        assertThat(p1p2.get().getCount()).isEqualTo(1);
    }

    @Test
    void should_return_top_n_rows_sorted_by_count_desc() {
        coOccurrenceRepository.deleteAll();
        userPurchaseRepository.deleteAll();
        mongoTemplate.getCollection("consumed_orders").drop();

        // p1 co-purchased many times with p2, fewer with p3, once with p4
        updater.ingestOrder("ord-D1", "u", List.of("p1", "p2"));
        updater.ingestOrder("ord-D2", "u", List.of("p1", "p2"));
        updater.ingestOrder("ord-D3", "u", List.of("p1", "p2"));
        updater.ingestOrder("ord-D4", "u", List.of("p1", "p3"));
        updater.ingestOrder("ord-D5", "u", List.of("p1", "p3"));
        updater.ingestOrder("ord-D6", "u", List.of("p1", "p4"));

        List<CoOccurrenceDocument> top = coOccurrenceRepository
                .findByProductIdOrderByCountDesc("p1", PageRequest.of(0, 10));

        assertThat(top).extracting(CoOccurrenceDocument::getOtherProductId)
                .containsExactly("p2", "p3", "p4");
        assertThat(top).extracting(CoOccurrenceDocument::getCount)
                .containsExactly(3L, 2L, 1L);
    }
}
