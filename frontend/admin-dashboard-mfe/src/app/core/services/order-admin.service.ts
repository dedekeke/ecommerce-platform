import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import {
  Order,
  PagedOrders,
  OrderFilterParams,
  UpdateOrderStatusPayload,
} from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderAdminService {
  private readonly api = inject(ApiClientService);

  getOrders(params: OrderFilterParams): Observable<PagedOrders> {
    const queryParams: Record<string, string | number | boolean> = {
      page: params.page,
      size: params.size,
    };
    if (params.status) queryParams['status'] = params.status;
    return this.api.get<PagedOrders>('/orders', queryParams);
  }

  getOrderById(id: string): Observable<Order> {
    return this.api.get<Order>(`/orders/${id}`);
  }

  updateOrderStatus(id: string, payload: UpdateOrderStatusPayload): Observable<Order> {
    return this.api.put<Order>(`/orders/${id}/status`, payload);
  }
}
