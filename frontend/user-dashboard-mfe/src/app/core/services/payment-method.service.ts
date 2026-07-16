import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { SavedPaymentMethod, SetupIntentResponse } from '../models/payment-method.model';

@Injectable({ providedIn: 'root' })
export class PaymentMethodService {
  private readonly api = inject(ApiClientService);

  list(userId: string): Observable<SavedPaymentMethod[]> {
    return this.api.get<SavedPaymentMethod[]>(`/payments/methods/user/${userId}`);
  }

  createSetupIntent(): Observable<SetupIntentResponse> {
    return this.api.post<SetupIntentResponse>('/payments/methods/setup-intent', {});
  }

  confirmSetupIntent(setupIntentId: string): Observable<SavedPaymentMethod> {
    return this.api.post<SavedPaymentMethod>('/payments/methods/confirm', { setupIntentId });
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`/payments/methods/${id}`);
  }

  setDefault(id: number): Observable<SavedPaymentMethod> {
    return this.api.put<SavedPaymentMethod>(`/payments/methods/${id}/default`, {});
  }
}
