import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import {
  AdminUser,
  PagedUsers,
  UserFilterParams,
  UpdateUserRolePayload,
} from '../models/user-admin.model';

@Injectable({ providedIn: 'root' })
export class UserAdminService {
  private readonly api = inject(ApiClientService);

  getUsers(params: UserFilterParams): Observable<PagedUsers> {
    const queryParams: Record<string, string | number | boolean> = {
      page: params.page,
      size: params.size,
    };
    if (params.search) queryParams['search'] = params.search;
    if (params.role) queryParams['role'] = params.role;
    if (params.status) queryParams['status'] = params.status;
    return this.api.get<PagedUsers>('/users', queryParams);
  }

  getUserById(id: string): Observable<AdminUser> {
    return this.api.get<AdminUser>(`/users/${id}`);
  }

  updateUserRole(id: string, payload: UpdateUserRolePayload): Observable<AdminUser> {
    return this.api.patch<AdminUser>(`/users/${id}/role`, payload);
  }
}
