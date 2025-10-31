package com.ecommerce.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base test class for microservices using MongoDB
 *
 * Services: Cart, Notification, Media
 *
 * Usage:
 * <pre>
 * @SpringBootTest
 * class CartServiceTest extends BaseMongoDBTest {
 *     // Your tests here
 * }
 * </pre>
 */
@SpringBootTest
@Testcontainers
public abstract class BaseMongoDBTest {

    @Container
    protected static final MongoDBContainer mongodb = new MongoDBContainer("mongo:7-jammy")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
    }
}
