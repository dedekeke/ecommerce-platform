package com.ecommerce.notificationservice;

import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

import java.time.Duration;

/**
 * Base test class for MongoDB integration tests using Testcontainers.
 *
 * Uses the singleton-container pattern: a single MongoDB container is started
 * once for the whole JVM (not per concrete subclass via {@code @Container}),
 * which avoids spinning up multiple replica-set containers in one fork and the
 * readiness races that caused intermittent "connection refused" /
 * WritableServerSelector timeouts. The container is reused across test classes
 * and torn down by Ryuk when the JVM exits.
 *
 * directConnection=true keeps the driver talking straight to the mapped port
 * instead of doing replica-set member discovery (which advertises an
 * unreachable internal container hostname).
 */
@DataMongoTest
public abstract class BaseMongoTest {

    static final MongoDBContainer MONGO_DB_CONTAINER =
            new MongoDBContainer("mongo:7-jammy")
                    .withStartupTimeout(Duration.ofMinutes(2));

    static {
        MONGO_DB_CONTAINER.start();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> MONGO_DB_CONTAINER.getReplicaSetUrl() + "?directConnection=true");
    }
}
