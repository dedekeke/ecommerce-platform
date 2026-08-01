import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import {
  Order,
  PagedAdminOrders,
  OrderFilterParams,
  UpdateOrderStatusPayload,
} from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderAdminService {
  private readonly api = inject(ApiClientService);

  getOrders(params: OrderFilterParams): Observable<PagedAdminOrders> {
    const queryParams: Record<string, string | number | boolean> = {
      page: params.page,
      size: params.size,
    };
    if (params.status) queryParams['status'] = params.status;
    return this.api.get<PagedAdminOrders>('/orders', queryParams);
  }

  getOrderById(id: string): Observable<Order> {
    return this.api.get<Order>(`/orders/${id}`);
  }

  /**
   * order-service's `PUT /orders/{id}/status` is `@RequestParam OrderStatus status` — a query
   * param, not a JSON body — so `status` is sent via `params`, not the request body.
   */
  updateOrderStatus(id: string, payload: UpdateOrderStatusPayload): Observable<Order> {
    return this.api.put<Order>(`/orders/${id}/status`, null, { status: payload.status });
  }
}
