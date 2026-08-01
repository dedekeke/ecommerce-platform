import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { UserProfile, UpdateProfilePayload, Address, CreateAddressPayload } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly api = inject(ApiClientService);

  getProfile(userId: string): Observable<UserProfile> {
    return this.api.get<UserProfile>(`/users/${userId}`);
  }

  updateProfile(userId: string, payload: UpdateProfilePayload): Observable<UserProfile> {
    return this.api.patch<UserProfile>(`/users/${userId}`, payload);
  }

  getAddresses(userId: string): Observable<Address[]> {
    return this.api.get<Address[]>(`/users/${userId}/addresses`);
  }

  createAddress(userId: string, payload: CreateAddressPayload): Observable<Address> {
    return this.api.post<Address>(`/users/${userId}/addresses`, payload);
  }

  updateAddress(userId: string, addressId: string, payload: CreateAddressPayload): Observable<Address> {
    return this.api.put<Address>(`/users/${userId}/addresses/${addressId}`, payload);
  }

  deleteAddress(userId: string, addressId: string): Observable<void> {
    return this.api.delete<void>(`/users/${userId}/addresses/${addressId}`);
  }
}
