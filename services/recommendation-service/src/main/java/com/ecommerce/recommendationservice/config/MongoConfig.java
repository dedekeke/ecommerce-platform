package com.ecommerce.recommendationservice.config;

import com.ecommerce.recommendationservice.domain.CoOccurrenceDocument;
import com.ecommerce.recommendationservice.domain.UserPurchaseDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Mongo index bootstrap.
 *
 * <p>{@code spring.data.mongodb.auto-index-creation=true} in application.yml
 * already creates the {@code @CompoundIndex} on {@link CoOccurrenceDocument}
 * and the {@code @Indexed} on {@link UserPurchaseDocument#getUserId()} on
 * first save. This class is a belt-and-braces guarantee: at startup we
 * idempotently {@code ensureIndex} everything we depend on, so brand-new
 * collections (or environments with auto-index disabled) still get the
 * indexes we need for the top-N query path.
 */
@Configuration
@EnableMongoAuditing
@RequiredArgsConstructor
@Slf4j
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        // co_occurrence: support per-product top-N read with a single IXSCAN.
        IndexOperations coOps = mongoTemplate.indexOps(CoOccurrenceDocument.class);
        coOps.ensureIndex(new Index()
                .on("productId", Sort.Direction.ASC)
                .on("count", Sort.Direction.DESC)
                .named("product_count_desc"));

        // user_purchases: userId is the @Id, so the default _id_ index already
        // covers our point-lookup; no extra index needed. We still touch
        // indexOps() to fail fast on connectivity issues at boot.
        IndexOperations userOps = mongoTemplate.indexOps(UserPurchaseDocument.class);
        userOps.getIndexInfo();

        log.info("Recommendation-service Mongo indexes ensured");
    }
}
