import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { ConvertCurrencyPayload, ConvertCurrencyResult, CurrencyRates } from '../models/currency.model';

/**
 * Calls promotion-service's CurrencyController: /api/currency/**.
 *
 * Note: the gateway currently has no Path predicate for /api/currency/** (it
 * only routes /api/promotions/**, /api/v1/promotions/** to promotion-service),
 * so these requests 404 through the gateway until that route is added — see
 * infrastructure/api-gateway/src/main/resources/application.yml. Tracked as a
 * backend/infra follow-up; this service is wired to the correct downstream
 * path so it starts working the moment the route exists. There is also no
 * admin write endpoint to set a rate — this screen is view/convert only.
 */
@Injectable({ providedIn: 'root' })
export class CurrencyAdminService {
  private readonly api = inject(ApiClientService);

  getRates(): Observable<CurrencyRates> {
    return this.api.get<CurrencyRates>('/currency/rates');
  }

  convert(payload: ConvertCurrencyPayload): Observable<ConvertCurrencyResult> {
    return this.api.post<ConvertCurrencyResult>('/currency/convert', payload);
  }
}
