package com.ecommerce.reviewservice.config;

import com.ecommerce.reviewservice.domain.ReviewDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Re-applies the {@code @CompoundIndexes} declared on {@link ReviewDocument}
 * at startup. Belt-and-braces: {@code spring.data.mongodb.auto-index-creation}
 * already covers the happy path, but explicit ensure-on-boot defends against
 * environments that disable auto-creation.
 */
@Configuration
@EnableMongoAuditing
@RequiredArgsConstructor
@Slf4j
public class MongoConfig {

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mappingContext;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        IndexOperations ops = mongoTemplate.indexOps(ReviewDocument.class);
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);
        resolver.resolveIndexFor(ReviewDocument.class).forEach(ops::ensureIndex);
        log.info("review-service Mongo indexes ensured");
    }
}
