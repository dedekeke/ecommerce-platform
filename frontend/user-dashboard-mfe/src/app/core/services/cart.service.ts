import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { AddToCartPayload, CartResponse } from '../models/cart.model';

/**
 * Talks to cart-service's authenticated cart API (POST /api/cart/items),
 * the same contract used by cart-mfe. The gateway resolves the user from
 * the JWT set by authInterceptor, so only productId/quantity are sent.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly api = inject(ApiClientService);

  addItem(payload: AddToCartPayload): Observable<CartResponse> {
    return this.api.post<CartResponse>('/cart/items', payload);
  }
}
