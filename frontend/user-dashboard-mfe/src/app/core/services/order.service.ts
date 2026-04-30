import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { Order, PagedOrders, OrderFilterParams } from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly api = inject(ApiClientService);

  getOrdersForUser(userId: string, params: OrderFilterParams): Observable<PagedOrders> {
    const queryParams: Record<string, string | number | boolean> = {
      page: params.page,
      size: params.size,
    };
    if (params.status) {
      queryParams['status'] = params.status;
    }
    return this.api.get<PagedOrders>(`/orders/user/${userId}`, queryParams);
  }

  getOrderById(orderId: string): Observable<Order> {
    return this.api.get<Order>(`/orders/${orderId}`);
  }
}
