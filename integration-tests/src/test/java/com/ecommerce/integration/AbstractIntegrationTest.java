package com.ecommerce.integration;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests with Testcontainers setup
 * Provides shared infrastructure containers for all integration tests
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    // PostgreSQL container for User, Order, Payment, Inventory, and Cart services
    protected static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:14-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    // Note: Cart service now uses PostgreSQL instead of MongoDB (updated configuration)

    // Kafka container for event publishing
    protected static final KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withReuse(true);

    // Redis container for caching
    protected static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @BeforeAll
    static void startContainers() {
        // Start all containers
        postgresContainer.start();
        kafkaContainer.start();
        redisContainer.start();

        // Wait for containers to be ready
        waitForContainer(postgresContainer);
        waitForContainer(kafkaContainer);
        waitForContainer(redisContainer);

        System.out.println("=".repeat(80));
        System.out.println("All integration test containers started successfully");
        System.out.println("PostgreSQL JDBC URL: " + postgresContainer.getJdbcUrl());
        System.out.println("Kafka Bootstrap Servers: " + kafkaContainer.getBootstrapServers());
        System.out.println("Redis Host: " + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379));
        System.out.println("=".repeat(80));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL properties for all services (User, Product, Cart, Order, Payment, Inventory)
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Kafka properties
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafkaContainer::getBootstrapServers);

        // Redis properties
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));

        // Disable Eureka for tests
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");

        // Disable security for tests
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");

        // Virtual threads enabled
        registry.add("spring.threads.virtual.enabled", () -> "true");

        // Kafka auto-create topics
        registry.add("spring.kafka.admin.auto-create", () -> "true");
    }

    private static void waitForContainer(org.testcontainers.containers.GenericContainer<?> container) {
        int maxRetries = 30;
        int retries = 0;
        while (!container.isRunning() && retries < maxRetries) {
            try {
                Thread.sleep(1000);
                retries++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for container", e);
            }
        }
        if (!container.isRunning()) {
            throw new RuntimeException("Container failed to start: " + container.getDockerImageName());
        }
    }

    /**
     * Get PostgreSQL JDBC URL for manual connections
     */
    protected String getPostgresJdbcUrl() {
        return postgresContainer.getJdbcUrl();
    }

    /**
     * Get Kafka bootstrap servers for manual connections
     */
    protected String getKafkaBootstrapServers() {
        return kafkaContainer.getBootstrapServers();
    }

    /**
     * Get Redis host for manual connections
     */
    protected String getRedisHost() {
        return redisContainer.getHost();
    }

    /**
     * Get Redis port for manual connections
     */
    protected Integer getRedisPort() {
        return redisContainer.getMappedPort(6379);
    }
}
