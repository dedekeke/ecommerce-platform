package com.ecommerce.common.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Infrastructure Integration Test
 *
 * Verifies that all infrastructure components (databases, message queues)
 * can be started and are accessible via Testcontainers.
 *
 * This test ensures that:
 * - PostgreSQL container starts and accepts connections
 * - MySQL container starts and accepts connections
 * - MongoDB container starts and accepts connections
 * - Kafka container starts and is accessible
 */
@Testcontainers
@DisplayName("Infrastructure Integration Tests")
class InfrastructureIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.2")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7-jammy");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @Test
    @DisplayName("PostgreSQL container should start and be accessible")
    void testPostgresConnection() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(postgres.getJdbcUrl()).isNotBlank();
        assertThat(postgres.getUsername()).isEqualTo("test");
        assertThat(postgres.getDatabaseName()).isEqualTo("testdb");
    }

    @Test
    @DisplayName("MySQL container should start and be accessible")
    void testMySQLConnection() {
        assertThat(mysql.isRunning()).isTrue();
        assertThat(mysql.getJdbcUrl()).isNotBlank();
        assertThat(mysql.getUsername()).isEqualTo("test");
        assertThat(mysql.getDatabaseName()).isEqualTo("testdb");
    }

    @Test
    @DisplayName("MongoDB container should start and be accessible")
    void testMongoDBConnection() {
        assertThat(mongodb.isRunning()).isTrue();
        assertThat(mongodb.getReplicaSetUrl()).isNotBlank();
    }

    @Test
    @DisplayName("Kafka container should start and be accessible")
    void testKafkaConnection() {
        assertThat(kafka.isRunning()).isTrue();
        assertThat(kafka.getBootstrapServers()).isNotBlank();
    }

    @Test
    @DisplayName("All infrastructure containers should be running")
    void testAllContainersRunning() {
        assertThat(postgres.isRunning())
                .as("PostgreSQL should be running")
                .isTrue();

        assertThat(mysql.isRunning())
                .as("MySQL should be running")
                .isTrue();

        assertThat(mongodb.isRunning())
                .as("MongoDB should be running")
                .isTrue();

        assertThat(kafka.isRunning())
                .as("Kafka should be running")
                .isTrue();
    }
}
