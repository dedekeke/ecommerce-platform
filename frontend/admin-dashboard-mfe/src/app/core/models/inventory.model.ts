export type InventoryStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'DISCONTINUED';

/** Mirrors inventory-service's InventoryResponse. */
export interface InventoryItem {
  id: string;
  productId: string;
  sku: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  status: InventoryStatus;
  reorderLevel: number;
  reorderQuantity: number;
  lastRestockedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** Body for POST /api/inventory (InventoryController's InventoryRequest). Admin-scoped. */
export interface CreateInventoryPayload {
  productId: string;
  sku: string;
  quantity: number;
  reorderLevel: number;
  reorderQuantity: number;
}

/** Body for PUT /api/inventory/product/{productId}/stock (StockUpdateRequest). Admin-scoped. */
export interface StockUpdatePayload {
  quantityChange: number;
  updateType: string;
  notes?: string;
}
