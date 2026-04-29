import { apiClient } from '../apiClient'
import type {
  UserProfile,
  Address,
  UpdateUserProfileRequest,
  CreateAddressRequest,
  UpdateAddressRequest,
} from '../types'

export const userService = {
  async getCurrentUser(): Promise<UserProfile> {
    const response = await apiClient.get<UserProfile>('/users/me')
    return response.data
  },

  async updateProfile(request: UpdateUserProfileRequest): Promise<UserProfile> {
    const response = await apiClient.put<UserProfile>('/users/me', request)
    return response.data
  },

  async getAddresses(): Promise<Address[]> {
    const response = await apiClient.get<Address[]>('/users/me/addresses')
    return response.data
  },

  async getAddressById(id: string): Promise<Address> {
    const response = await apiClient.get<Address>(`/users/me/addresses/${id}`)
    return response.data
  },

  async createAddress(request: CreateAddressRequest): Promise<Address> {
    const response = await apiClient.post<Address>(
      '/users/me/addresses',
      request
    )
    return response.data
  },

  async updateAddress(
    id: string,
    request: UpdateAddressRequest
  ): Promise<Address> {
    const response = await apiClient.put<Address>(
      `/users/me/addresses/${id}`,
      request
    )
    return response.data
  },

  async deleteAddress(id: string): Promise<void> {
    await apiClient.delete(`/users/me/addresses/${id}`)
  },

  async setDefaultAddress(id: string): Promise<Address> {
    const response = await apiClient.put<Address>(
      `/users/me/addresses/${id}/default`
    )
    return response.data
  },

  async getWishlist(): Promise<string[]> {
    const response = await apiClient.get<string[]>('/users/me/wishlist')
    return response.data
  },

  async addToWishlist(productId: string): Promise<void> {
    await apiClient.post(`/users/me/wishlist/${productId}`)
  },

  async removeFromWishlist(productId: string): Promise<void> {
    await apiClient.delete(`/users/me/wishlist/${productId}`)
  },

  async isInWishlist(productId: string): Promise<boolean> {
    const response = await apiClient.get<{ inWishlist: boolean }>(
      `/users/me/wishlist/${productId}/check`
    )
    return response.data.inWishlist
  },
}

export default userService
