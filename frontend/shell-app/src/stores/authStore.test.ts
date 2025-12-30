import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from './authStore'

const mockUser = {
  sub: 'auth0|123456789',
  email: 'test@example.com',
  email_verified: true,
  name: 'Test User',
  nickname: 'testuser',
  picture: 'https://example.com/avatar.jpg',
  updated_at: '2024-01-01T00:00:00.000Z',
}

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
  })

  describe('initial state', () => {
    it('should have null token initially', () => {
      const { token } = useAuthStore.getState()
      expect(token).toBeNull()
    })

    it('should have null user initially', () => {
      const { user } = useAuthStore.getState()
      expect(user).toBeNull()
    })

    it('should not be authenticated initially', () => {
      const { isAuthenticated } = useAuthStore.getState()
      expect(isAuthenticated).toBe(false)
    })
  })

  describe('setAuth', () => {
    it('should set token and user', () => {
      useAuthStore.getState().setAuth('test-token', mockUser)

      const { token, user } = useAuthStore.getState()
      expect(token).toBe('test-token')
      expect(user).toEqual(mockUser)
    })

    it('should set isAuthenticated to true', () => {
      useAuthStore.getState().setAuth('test-token', mockUser)

      const { isAuthenticated } = useAuthStore.getState()
      expect(isAuthenticated).toBe(true)
    })
  })

  describe('clearAuth', () => {
    it('should clear token and user', () => {
      useAuthStore.getState().setAuth('test-token', mockUser)
      useAuthStore.getState().clearAuth()

      const { token, user } = useAuthStore.getState()
      expect(token).toBeNull()
      expect(user).toBeNull()
    })

    it('should set isAuthenticated to false', () => {
      useAuthStore.getState().setAuth('test-token', mockUser)
      useAuthStore.getState().clearAuth()

      const { isAuthenticated } = useAuthStore.getState()
      expect(isAuthenticated).toBe(false)
    })
  })

  describe('selectors', () => {
    it('should select user email', () => {
      useAuthStore.getState().setAuth('test-token', mockUser)

      const { user } = useAuthStore.getState()
      expect(user?.email).toBe('test@example.com')
    })

    it('should select user name', () => {
      useAuthStore.getState().setAuth('test-token', mockUser)

      const { user } = useAuthStore.getState()
      expect(user?.name).toBe('Test User')
    })
  })
})
