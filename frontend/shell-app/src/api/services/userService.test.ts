import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { userService } from './userService'
import { apiClient } from '../apiClient'
import type {
  UserProfile,
  Address,
  UpdateUserProfileRequest,
  CreateAddressRequest,
  UpdateAddressRequest,
} from '../types'

vi.mock('../apiClient', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockAddress: Address = {
  id: 'addr-1',
  label: 'Home',
  street: '123 Main St',
  city: 'Springfield',
  state: 'IL',
  postalCode: '62701',
  country: 'US',
  isDefault: true,
}

const mockUserProfile: UserProfile = {
  id: 'user-1',
  auth0Id: 'auth0|123456',
  email: 'john@example.com',
  firstName: 'John',
  lastName: 'Doe',
  phoneNumber: '+1234567890',
  defaultAddressId: 'addr-1',
  addresses: [mockAddress],
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
}

describe('UserService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getCurrentUser', () => {
    it('should fetch current user profile', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockUserProfile })

      const result = await userService.getCurrentUser()

      expect(apiClient.get).toHaveBeenCalledWith('/users/me')
      expect(result).toEqual(mockUserProfile)
    })
  })

  describe('updateProfile', () => {
    it('should update user profile', async () => {
      const updatedProfile = { ...mockUserProfile, firstName: 'Jane' }
      vi.mocked(apiClient.put).mockResolvedValue({ data: updatedProfile })

      const request: UpdateUserProfileRequest = {
        firstName: 'Jane',
      }

      const result = await userService.updateProfile(request)

      expect(apiClient.put).toHaveBeenCalledWith('/users/me', request)
      expect(result.firstName).toBe('Jane')
    })

    it('should update multiple fields', async () => {
      const updatedProfile = {
        ...mockUserProfile,
        firstName: 'Jane',
        lastName: 'Smith',
        phoneNumber: '+0987654321',
      }
      vi.mocked(apiClient.put).mockResolvedValue({ data: updatedProfile })

      const request: UpdateUserProfileRequest = {
        firstName: 'Jane',
        lastName: 'Smith',
        phoneNumber: '+0987654321',
      }

      const result = await userService.updateProfile(request)

      expect(apiClient.put).toHaveBeenCalledWith('/users/me', request)
      expect(result).toEqual(updatedProfile)
    })
  })

  describe('getAddresses', () => {
    it('should fetch user addresses', async () => {
      const addresses = [mockAddress, { ...mockAddress, id: 'addr-2', label: 'Work', isDefault: false }]
      vi.mocked(apiClient.get).mockResolvedValue({ data: addresses })

      const result = await userService.getAddresses()

      expect(apiClient.get).toHaveBeenCalledWith('/users/me/addresses')
      expect(result).toEqual(addresses)
    })

    it('should return empty array when no addresses', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] })

      const result = await userService.getAddresses()

      expect(result).toHaveLength(0)
    })
  })

  describe('getAddressById', () => {
    it('should fetch a single address by ID', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockAddress })

      const result = await userService.getAddressById('addr-1')

      expect(apiClient.get).toHaveBeenCalledWith('/users/me/addresses/addr-1')
      expect(result).toEqual(mockAddress)
    })
  })

  describe('createAddress', () => {
    it('should create a new address', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockAddress })

      const request: CreateAddressRequest = {
        label: 'Home',
        street: '123 Main St',
        city: 'Springfield',
        state: 'IL',
        postalCode: '62701',
        country: 'US',
        isDefault: true,
      }

      const result = await userService.createAddress(request)

      expect(apiClient.post).toHaveBeenCalledWith('/users/me/addresses', request)
      expect(result).toEqual(mockAddress)
    })
  })

  describe('updateAddress', () => {
    it('should update an existing address', async () => {
      const updatedAddress = { ...mockAddress, street: '456 Oak Ave' }
      vi.mocked(apiClient.put).mockResolvedValue({ data: updatedAddress })

      const request: UpdateAddressRequest = {
        street: '456 Oak Ave',
      }

      const result = await userService.updateAddress('addr-1', request)

      expect(apiClient.put).toHaveBeenCalledWith('/users/me/addresses/addr-1', request)
      expect(result.street).toBe('456 Oak Ave')
    })
  })

  describe('deleteAddress', () => {
    it('should delete an address', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ data: undefined })

      await userService.deleteAddress('addr-1')

      expect(apiClient.delete).toHaveBeenCalledWith('/users/me/addresses/addr-1')
    })
  })

  describe('setDefaultAddress', () => {
    it('should set address as default', async () => {
      const updatedAddress = { ...mockAddress, isDefault: true }
      vi.mocked(apiClient.put).mockResolvedValue({ data: updatedAddress })

      const result = await userService.setDefaultAddress('addr-1')

      expect(apiClient.put).toHaveBeenCalledWith('/users/me/addresses/addr-1/default')
      expect(result.isDefault).toBe(true)
    })
  })

  describe('getWishlist', () => {
    it('should fetch user wishlist', async () => {
      const wishlistItems = ['prod-1', 'prod-2', 'prod-3']
      vi.mocked(apiClient.get).mockResolvedValue({ data: wishlistItems })

      const result = await userService.getWishlist()

      expect(apiClient.get).toHaveBeenCalledWith('/users/me/wishlist')
      expect(result).toEqual(wishlistItems)
    })
  })

  describe('addToWishlist', () => {
    it('should add product to wishlist', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: { success: true } })

      await userService.addToWishlist('prod-1')

      expect(apiClient.post).toHaveBeenCalledWith('/users/me/wishlist/prod-1')
    })
  })

  describe('removeFromWishlist', () => {
    it('should remove product from wishlist', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ data: undefined })

      await userService.removeFromWishlist('prod-1')

      expect(apiClient.delete).toHaveBeenCalledWith('/users/me/wishlist/prod-1')
    })
  })

  describe('isInWishlist', () => {
    it('should check if product is in wishlist', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: { inWishlist: true } })

      const result = await userService.isInWishlist('prod-1')

      expect(apiClient.get).toHaveBeenCalledWith('/users/me/wishlist/prod-1/check')
      expect(result).toBe(true)
    })

    it('should return false when product not in wishlist', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: { inWishlist: false } })

      const result = await userService.isInWishlist('prod-999')

      expect(result).toBe(false)
    })
  })
})
