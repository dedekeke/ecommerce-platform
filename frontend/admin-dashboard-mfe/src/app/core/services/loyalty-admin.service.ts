import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { LoyaltyStatus } from '../models/loyalty.model';

/**
 * Calls promotion-service's LoyaltyController via the gateway:
 * /api/promotions/loyalty/{userId}.
 *
 * Note: the backend currently only exposes a per-user tier lookup — there is
 * no admin endpoint to list or edit tier thresholds, so this admin screen is
 * read-only (lookup). Tier CRUD is a backend follow-up.
 */
@Injectable({ providedIn: 'root' })
export class LoyaltyAdminService {
  private readonly api = inject(ApiClientService);

  getLoyaltyStatus(userId: string): Observable<LoyaltyStatus> {
    return this.api.get<LoyaltyStatus>(`/promotions/loyalty/${userId}`);
  }
}
