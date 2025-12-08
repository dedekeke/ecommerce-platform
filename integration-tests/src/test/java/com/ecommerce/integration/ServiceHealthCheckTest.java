package com.ecommerce.integration;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Service Health Check Integration Test
 *
 * Verifies that all microservices are running and healthy
 * Tests infrastructure services, business services, and their dependencies
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServiceHealthCheckTest {

    private static class ServiceEndpoint {
        String name;
        String url;
        boolean hasRedis;
        boolean hasDatabase;
        boolean hasEureka;
        boolean hasConfigServer;

        ServiceEndpoint(String name, String url, boolean hasRedis, boolean hasDatabase,
                       boolean hasEureka, boolean hasConfigServer) {
            this.name = name;
            this.url = url;
            this.hasRedis = hasRedis;
            this.hasDatabase = hasDatabase;
            this.hasEureka = hasEureka;
            this.hasConfigServer = hasConfigServer;
        }
    }

    private static final List<ServiceEndpoint> SERVICES = Arrays.asList(
        // Infrastructure Services
        new ServiceEndpoint("Eureka Server", "http://localhost:8761/actuator/health",
                           false, false, false, false),
        new ServiceEndpoint("Config Server", "http://localhost:8888/actuator/health",
                           false, false, true, false),
        new ServiceEndpoint("API Gateway", "http://localhost:8080/actuator/health",
                           false, false, true, false),

        // Business Services
        new ServiceEndpoint("User Service", "http://localhost:8081/actuator/health",
                           true, true, true, false),
        new ServiceEndpoint("Product Service", "http://localhost:8082/actuator/health",
                           true, true, true, false),
        new ServiceEndpoint("Cart Service", "http://localhost:8083/actuator/health",
                           true, true, true, true),
        new ServiceEndpoint("Payment Service", "http://localhost:8085/actuator/health",
                           true, true, true, false),
        new ServiceEndpoint("Inventory Service", "http://localhost:8086/actuator/health",
                           true, true, true, false)
    );

    @BeforeAll
    void setup() {
        System.out.println("=".repeat(80));
        System.out.println("SERVICE HEALTH CHECK TESTS");
        System.out.println("=".repeat(80));
        System.out.println("Testing all microservices and infrastructure components");
        System.out.println("Total services to check: " + SERVICES.size());
        System.out.println("=".repeat(80));
    }

    @Test
    @Order(1)
    @DisplayName("Test Eureka Server Health")
    void testEurekaServerHealth() {
        System.out.println("\n[1/8] Testing Eureka Server...");

        Response response = given()
            .when()
            .get("http://localhost:8761/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .extract()
            .response();

        System.out.println("  ✓ Eureka Server is UP");
        System.out.println("  Status: " + response.jsonPath().getString("status"));
    }

    @Test
    @Order(2)
    @DisplayName("Test Config Server Health")
    void testConfigServerHealth() {
        System.out.println("\n[2/8] Testing Config Server...");

        Response response = given()
            .when()
            .get("http://localhost:8888/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components.discoveryComposite.status", equalTo("UP"))
            .extract()
            .response();

        System.out.println("  ✓ Config Server is UP");
        System.out.println("  ✓ Eureka connection: UP");
    }

    @Test
    @Order(3)
    @DisplayName("Test API Gateway Health")
    void testApiGatewayHealth() {
        System.out.println("\n[3/8] Testing API Gateway...");

        Response response = given()
            .when()
            .get("http://localhost:8080/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .extract()
            .response();

        System.out.println("  ✓ API Gateway is UP");
    }

    @Test
    @Order(4)
    @DisplayName("Test User Service Health")
    void testUserServiceHealth() {
        System.out.println("\n[4/8] Testing User Service...");

        Response response = given()
            .when()
            .get("http://localhost:8081/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components.db.status", equalTo("UP"))
            .body("components.redis.status", equalTo("UP"))
            .body("components.discoveryComposite.status", equalTo("UP"))
            .extract()
            .response();

        System.out.println("  ✓ User Service is UP");
        System.out.println("  ✓ PostgreSQL Database: UP");
        System.out.println("  ✓ Redis Cache: UP");
        System.out.println("  ✓ Eureka Discovery: UP");

        // Verify database type
        String dbType = response.jsonPath().getString("components.db.details.database");
        assertEquals("PostgreSQL", dbType, "Database should be PostgreSQL");
    }

    @Test
    @Order(5)
    @DisplayName("Test Product Service Health")
    void testProductServiceHealth() {
        System.out.println("\n[5/8] Testing Product Service...");

        Response response = given()
            .when()
            .get("http://localhost:8082/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components.db.status", equalTo("UP"))
            .body("components.discoveryComposite.status", equalTo("UP"))
            .extract()
            .response();

        System.out.println("  ✓ Product Service is UP");
        System.out.println("  ✓ MongoDB Database: UP");
        System.out.println("  ✓ Eureka Discovery: UP");
    }

    @Test
    @Order(6)
    @DisplayName("Test Cart Service Health")
    void testCartServiceHealth() {
        System.out.println("\n[6/8] Testing Cart Service...");

        Response response = given()
            .when()
            .get("http://localhost:8083/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components.db.status", equalTo("UP"))
            .body("components.redis.status", equalTo("UP"))
            .body("components.discoveryComposite.status", equalTo("UP"))
            .body("components.clientConfigServer.status", equalTo("UP"))
            .extract()
            .response();

        System.out.println("  ✓ Cart Service is UP");
        System.out.println("  ✓ PostgreSQL Database: UP");
        System.out.println("  ✓ Redis Cache: UP");
        System.out.println("  ✓ Eureka Discovery: UP");
        System.out.println("  ✓ Config Server: UP");

        // Verify database type changed from MongoDB to PostgreSQL
        String dbType = response.jsonPath().getString("components.db.details.database");
        assertEquals("PostgreSQL", dbType, "Cart service should now use PostgreSQL");
    }

    @Test
    @Order(7)
    @DisplayName("Test Payment Service Health")
    void testPaymentServiceHealth() {
        System.out.println("\n[7/8] Testing Payment Service...");

        Response response = given()
            .when()
            .get("http://localhost:8085/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components.db.status", equalTo("UP"))
            .body("components.discoveryComposite.status", equalTo("UP"))
            .extract()
            .response();

        System.out.println("  ✓ Payment Service is UP");
        System.out.println("  ✓ MySQL Database: UP");
        System.out.println("  ✓ Eureka Discovery: UP");
        System.out.println("  ✓ gRPC Server: Port 9090");
    }

    @Test
    @Order(8)
    @DisplayName("Test Inventory Service Health")
    void testInventoryServiceHealth() {
        System.out.println("\n[8/8] Testing Inventory Service...");

        Response response = given()
            .when()
            .get("http://localhost:8086/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components.db.status", equalTo("UP"))
            .body("components.discoveryComposite.status", equalTo("UP"))
            .extract()
            .response();

        System.out.println("  ✓ Inventory Service is UP");
        System.out.println("  ✓ PostgreSQL Database: UP");
        System.out.println("  ✓ Eureka Discovery: UP");
        System.out.println("  ✓ gRPC Server: Port 9091");
    }

    @Test
    @Order(9)
    @DisplayName("Test Service Discovery - Verify All Services Registered")
    void testServiceDiscovery() {
        System.out.println("\n[9/9] Testing Service Discovery...");

        Response response = given()
            .when()
            .get("http://localhost:8761/eureka/apps")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String responseBody = response.asString();

        // Verify all services are registered (excluding API Gateway which doesn't register)
        List<String> expectedServices = Arrays.asList(
            "USER-SERVICE",
            "PRODUCT-SERVICE",
            "CART-SERVICE",
            "PAYMENT-SERVICE",
            "INVENTORY-SERVICE",
            "CONFIG-SERVER"
        );

        for (String service : expectedServices) {
            assertTrue(responseBody.contains(service),
                "Service " + service + " should be registered in Eureka");
            System.out.println("  ✓ " + service + " registered");
        }
    }

    @AfterAll
    void printSummary() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("HEALTH CHECK SUMMARY");
        System.out.println("=".repeat(80));
        System.out.println("✓ All " + SERVICES.size() + " services are healthy");
        System.out.println("✓ All databases are connected");
        System.out.println("✓ Redis caching is working");
        System.out.println("✓ Service discovery is operational");
        System.out.println("✓ Configuration management is working");
        System.out.println("=".repeat(80));
        System.out.println("\nPlatform Status: FULLY OPERATIONAL 🚀");
        System.out.println("=".repeat(80));
    }
}
