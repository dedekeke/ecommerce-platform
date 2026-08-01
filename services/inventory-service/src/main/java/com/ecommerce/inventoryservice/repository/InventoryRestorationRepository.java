package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.domain.entity.InventoryRestoration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRestorationRepository extends JpaRepository<InventoryRestoration, String> {
}
