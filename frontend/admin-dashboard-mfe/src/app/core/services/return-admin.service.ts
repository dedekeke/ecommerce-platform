import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { InspectReturnPayload, ReturnRequest } from '../models/return.model';

/**
 * Calls order-service's saga/rma RmaController (returns saga) via the
 * gateway: /api/returns/**. Admin-only actions (receive/inspect) require
 * SCOPE_admin, enforced server-side.
 */
@Injectable({ providedIn: 'root' })
export class ReturnAdminService {
  private readonly api = inject(ApiClientService);

  getReturnById(rmaId: string): Observable<ReturnRequest> {
    return this.api.get<ReturnRequest>(`/returns/${rmaId}`);
  }

  getReturnsByUser(userId: string): Observable<ReturnRequest[]> {
    return this.api.get<ReturnRequest[]>(`/returns/user/${userId}`);
  }

  markReceived(rmaId: string): Observable<ReturnRequest> {
    return this.api.post<ReturnRequest>(`/returns/${rmaId}/receive`, {});
  }

  inspect(rmaId: string, payload: InspectReturnPayload): Observable<ReturnRequest> {
    return this.api.post<ReturnRequest>(`/returns/${rmaId}/inspect`, payload);
  }
}
