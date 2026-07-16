import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { CreateInventoryPayload, InventoryItem, StockUpdatePayload } from '../models/inventory.model';

/**
 * Calls inventory-service's InventoryController via the gateway:
 * /api/inventory/**. Create/adjust/low-stock/reorder-needed require
 * SCOPE_admin, enforced server-side.
 *
 * Note: the backend has no "list all inventory" or reservations-list REST
 * endpoint (reservations are managed internally via gRPC from order-service)
 * and no endpoint to manually trigger replenishment (it's a log-only
 * scheduled job — see InventoryScheduledTasks). The admin UI surfaces the
 * low-stock/reorder-needed slices plus a per-product/SKU lookup as the
 * practical "stock levels" view, and treats "Adjust Stock" against a
 * reorder-needed row as the restock action in place of a dedicated trigger.
 */
@Injectable({ providedIn: 'root' })
export class InventoryAdminService {
  private readonly api = inject(ApiClientService);

  getByProductId(productId: string): Observable<InventoryItem> {
    return this.api.get<InventoryItem>(`/inventory/product/${productId}`);
  }

  getBySku(sku: string): Observable<InventoryItem> {
    return this.api.get<InventoryItem>(`/inventory/sku/${sku}`);
  }

  getLowStockItems(): Observable<InventoryItem[]> {
    return this.api.get<InventoryItem[]>('/inventory/low-stock');
  }

  getItemsNeedingReorder(): Observable<InventoryItem[]> {
    return this.api.get<InventoryItem[]>('/inventory/reorder-needed');
  }

  createInventory(payload: CreateInventoryPayload): Observable<InventoryItem> {
    return this.api.post<InventoryItem>('/inventory', payload);
  }

  updateStock(productId: string, payload: StockUpdatePayload): Observable<InventoryItem> {
    return this.api.put<InventoryItem>(`/inventory/product/${productId}/stock`, payload);
  }
}
