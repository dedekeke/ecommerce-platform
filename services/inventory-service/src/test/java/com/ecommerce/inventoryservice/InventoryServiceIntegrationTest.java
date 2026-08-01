package com.ecommerce.inventoryservice;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.InventoryReservation;
import com.ecommerce.inventoryservice.domain.enums.InventoryStatus;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import com.ecommerce.inventoryservice.repository.InventoryReservationRepository;
import com.ecommerce.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class InventoryServiceIntegrationTest {

//    @Container
//    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
//            .withDatabaseName("inventorydb")
//            .withUsername("admin")
//            .withPassword("admin123");
//
//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
//    }

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    private Inventory testInventory;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        reservationRepository.deleteAll();

        testInventory = Inventory.builder()
                .productId("test-product-1")
                .sku("TEST-SKU-001")
                .quantity(100)
                .reservedQuantity(0)
                .status(InventoryStatus.IN_STOCK)
                .reorderLevel(10)
                .reorderQuantity(50)
                .build();

        testInventory = inventoryRepository.save(testInventory);
    }

    @Test
    void testCreateInventory() {
        Inventory inventory = Inventory.builder()
                .productId("test-product-2")
                .sku("TEST-SKU-002")
                .quantity(50)
                .reorderLevel(5)
                .reorderQuantity(25)
                .build();

        Inventory saved = inventoryService.createOrUpdateInventory(inventory);

        assertNotNull(saved.getId());
        assertEquals("test-product-2", saved.getProductId());
        assertEquals(50, saved.getQuantity());
        assertEquals(0, saved.getReservedQuantity());
        assertEquals(50, saved.getAvailableQuantity());
    }

    @Test
    void testReserveStock() {
        String orderId = "order-123";
        Integer quantity = 10;

        InventoryReservation reservation = inventoryService.reserveStock(
                orderId,
                testInventory.getProductId(),
                quantity,
                15
        );

        assertNotNull(reservation.getId());
        assertEquals(orderId, reservation.getOrderId());
        assertEquals(testInventory.getProductId(), reservation.getProductId());
        assertEquals(quantity, reservation.getQuantity());

        Inventory updated = inventoryService.getInventoryByProductId(testInventory.getProductId());
        assertEquals(10, updated.getReservedQuantity());
        assertEquals(90, updated.getAvailableQuantity());
    }

    @Test
    void testCommitReservation() {
        String orderId = "order-456";
        Integer quantity = 20;

        InventoryReservation reservation = inventoryService.reserveStock(
                orderId,
                testInventory.getProductId(),
                quantity,
                15
        );

        inventoryService.commitReservation(reservation.getId());

        Inventory updated = inventoryService.getInventoryByProductId(testInventory.getProductId());
        assertEquals(80, updated.getQuantity());
        assertEquals(0, updated.getReservedQuantity());
        assertEquals(80, updated.getAvailableQuantity());
    }

    @Test
    void testReleaseReservation() {
        String orderId = "order-789";
        Integer quantity = 15;

        InventoryReservation reservation = inventoryService.reserveStock(
                orderId,
                testInventory.getProductId(),
                quantity,
                15
        );

        inventoryService.releaseReservation(reservation.getId());

        Inventory updated = inventoryService.getInventoryByProductId(testInventory.getProductId());
        assertEquals(100, updated.getQuantity());
        assertEquals(0, updated.getReservedQuantity());
        assertEquals(100, updated.getAvailableQuantity());
    }

    @Test
    void testCheckAvailability() {
        assertTrue(inventoryService.checkAvailability(testInventory.getProductId(), 50));
        assertTrue(inventoryService.checkAvailability(testInventory.getProductId(), 100));
        assertFalse(inventoryService.checkAvailability(testInventory.getProductId(), 101));
    }

    @Test
    void testUpdateStock() {
        Inventory updated = inventoryService.updateStock(
                testInventory.getProductId(),
                50,
                "RESTOCK",
                "Restocking inventory"
        );

        assertEquals(150, updated.getQuantity());
        assertEquals(150, updated.getAvailableQuantity());
        assertNotNull(updated.getLastRestockedAt());
    }
}
