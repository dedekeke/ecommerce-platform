import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { PagedRefunds, RefundFilterParams, RefundSagaState, StartRefundPayload } from '../models/refund.model';

/**
 * Calls order-service's RefundController (orchestrated refund saga,
 * admin-scoped) via the gateway: /api/orders/{orderId}/refund and
 * /api/orders/refunds/{sagaId}.
 */
@Injectable({ providedIn: 'root' })
export class RefundAdminService {
  private readonly api = inject(ApiClientService);

  startRefund(orderId: string, payload: StartRefundPayload): Observable<RefundSagaState> {
    return this.api.post<RefundSagaState>(`/orders/${orderId}/refund`, payload);
  }

  getRefundSaga(sagaId: string): Observable<RefundSagaState> {
    return this.api.get<RefundSagaState>(`/orders/refunds/${sagaId}`);
  }

  getRefunds(params: RefundFilterParams): Observable<PagedRefunds> {
    const queryParams: Record<string, string | number | boolean> = {
      page: params.page,
      size: params.size,
    };
    if (params.status) queryParams['status'] = params.status;
    return this.api.get<PagedRefunds>('/orders/refunds', queryParams);
  }
}
