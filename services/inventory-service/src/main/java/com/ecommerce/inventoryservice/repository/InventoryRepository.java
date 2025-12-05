package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.enums.InventoryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {

    /**
     * Find inventory by product ID
     */
    Optional<Inventory> findByProductId(String productId);

    /**
     * Find inventory by product ID with pessimistic write lock
     * This ensures exclusive access for stock reservation
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") String productId);

    /**
     * Find inventory by SKU
     */
    Optional<Inventory> findBySku(String sku);

    /**
     * Find inventory by SKU with pessimistic write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.sku = :sku")
    Optional<Inventory> findBySkuForUpdate(@Param("sku") String sku);

    /**
     * Find all inventory items by status
     */
    List<Inventory> findByStatus(InventoryStatus status);

    /**
     * Find inventory items that need reordering
     */
    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reservedQuantity) <= i.reorderLevel AND i.status != 'DISCONTINUED'")
    List<Inventory> findItemsNeedingReorder();

    /**
     * Find low stock items
     */
    List<Inventory> findByStatusIn(List<InventoryStatus> statuses);

    /**
     * Check if product exists
     */
    boolean existsByProductId(String productId);

    /**
     * Check if SKU exists
     */
    boolean existsBySku(String sku);
}
