import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from './apiClient'
import { clearGuestCart, addGuestCartItem, pushCartToGuestCart } from './guestCartService'

vi.mock('./apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const axios404 = () =>
  Object.assign(new Error('Not Found'), {
    isAxiosError: true,
    response: { status: 404 },
  })

describe('guestCartService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('clearGuestCart', () => {
    it('should DELETE /cart/guest/clear with the X-Guest-Email header and no toast', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ status: 204 })

      await clearGuestCart('guest@example.com')

      expect(apiClient.delete).toHaveBeenCalledWith('/cart/guest/clear', {
        headers: { 'X-Guest-Email': 'guest@example.com' },
        skipErrorToast: true,
      })
    })

    it('should treat 404 (no guest cart yet) as success', async () => {
      vi.mocked(apiClient.delete).mockRejectedValue(axios404())

      await expect(clearGuestCart('guest@example.com')).resolves.toBeUndefined()
    })

    it('should rethrow non-404 failures', async () => {
      vi.mocked(apiClient.delete).mockRejectedValue(new Error('boom'))

      await expect(clearGuestCart('guest@example.com')).rejects.toThrow('boom')
    })
  })

  describe('addGuestCartItem', () => {
    it('should POST the line to /cart/guest/items with the X-Guest-Email header', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ status: 200 })

      await addGuestCartItem('guest@example.com', { productId: 'p1', quantity: 2 })

      expect(apiClient.post).toHaveBeenCalledWith(
        '/cart/guest/items',
        { productId: 'p1', quantity: 2 },
        { headers: { 'X-Guest-Email': 'guest@example.com' }, skipErrorToast: true }
      )
    })
  })

  describe('pushCartToGuestCart', () => {
    it('should clear first, then push every line (clear-then-push avoids double-counting)', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ status: 204 })
      vi.mocked(apiClient.post).mockResolvedValue({ status: 200 })

      await pushCartToGuestCart('guest@example.com', [
        { productId: 'p1', quantity: 2 },
        { productId: 'p2', quantity: 1 },
      ])

      expect(apiClient.delete).toHaveBeenCalledTimes(1)
      expect(apiClient.post).toHaveBeenCalledTimes(2)
      const deleteOrder = vi.mocked(apiClient.delete).mock.invocationCallOrder[0]
      const firstPostOrder = vi.mocked(apiClient.post).mock.invocationCallOrder[0]
      expect(deleteOrder).toBeLessThan(firstPostOrder as number)
    })

    it('should propagate a line-push failure so checkout never submits a partial cart silently', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ status: 204 })
      vi.mocked(apiClient.post).mockRejectedValue(new Error('product unavailable'))

      await expect(
        pushCartToGuestCart('guest@example.com', [{ productId: 'p1', quantity: 1 }])
      ).rejects.toThrow('product unavailable')
    })
  })
})
