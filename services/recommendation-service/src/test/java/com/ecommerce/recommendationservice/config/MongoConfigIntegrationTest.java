package com.ecommerce.recommendationservice.config;

import com.ecommerce.recommendationservice.domain.CoOccurrenceDocument;
import com.ecommerce.recommendationservice.domain.UserPurchaseDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link MongoConfig} index-bootstrap event listener actually
 * creates the indexes on Mongo. Important because the top-N read path's
 * latency budget assumes {@code product_count_desc} is in place.
 */
@DataMongoTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@Import(MongoConfig.class)
@Testcontainers
class MongoConfigIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7-jammy")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoConfig mongoConfig;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void should_create_compound_index_on_product_count_desc() {
        // Seed both collections so Mongo materialises them before index assertions.
        mongoTemplate.save(CoOccurrenceDocument.builder()
                .id("p1:p2").productId("p1").otherProductId("p2").count(1).build());
        mongoTemplate.save(UserPurchaseDocument.builder()
                .userId("user-x").productIds(java.util.Set.of("p1")).build());

        mongoConfig.ensureIndexes();

        List<Document> indexes = mongoTemplate.getCollection("co_occurrence")
                .listIndexes()
                .into(new java.util.ArrayList<>());

        assertThat(indexes)
                .anyMatch(d -> "product_count_desc".equals(d.getString("name")));

        List<Document> userIndexes = mongoTemplate.getCollection(
                mongoTemplate.getCollectionName(UserPurchaseDocument.class))
                .listIndexes()
                .into(new java.util.ArrayList<>());

        // userId is the @Id field → mapped to _id, so the default _id_ index
        // already covers all point-lookups. We just assert that index exists.
        assertThat(userIndexes)
                .anyMatch(d -> "_id_".equals(d.getString("name")));
    }
}
