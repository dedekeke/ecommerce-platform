import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { Promotion, PromotionPayload } from '../models/promotion.model';

/**
 * Calls promotion-service's PromotionController via the gateway:
 * /api/promotions/**. Create/update/delete require SCOPE_admin, enforced
 * server-side (see PromotionController).
 *
 * Note: GET /api/promotions returns only currently-active promotions and
 * does not support server-side pagination/filtering — the admin UI filters
 * client-side.
 */
@Injectable({ providedIn: 'root' })
export class PromotionAdminService {
  private readonly api = inject(ApiClientService);

  getPromotions(): Observable<Promotion[]> {
    return this.api.get<Promotion[]>('/promotions');
  }

  getPromotionById(id: number): Observable<Promotion> {
    return this.api.get<Promotion>(`/promotions/${id}`);
  }

  getPromotionByCode(code: string): Observable<Promotion> {
    return this.api.get<Promotion>(`/promotions/code/${code}`);
  }

  createPromotion(payload: PromotionPayload): Observable<Promotion> {
    return this.api.post<Promotion>('/promotions', payload);
  }

  updatePromotion(id: number, payload: PromotionPayload): Observable<Promotion> {
    return this.api.put<Promotion>(`/promotions/${id}`, payload);
  }

  deletePromotion(id: number): Observable<void> {
    return this.api.delete<void>(`/promotions/${id}`);
  }
}
