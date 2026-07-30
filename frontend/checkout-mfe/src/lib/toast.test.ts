import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { toast } from './toast'

describe('toast', () => {
  afterEach(() => {
    delete window.__ecommerceToastHost
    vi.restoreAllMocks()
  })

  describe('when the shell toast host is mounted', () => {
    beforeEach(() => {
      window.__ecommerceToastHost = true
    })

    it.each([
      ['success', 'Added to cart'],
      ['error', 'Something went wrong'],
      ['warning', 'Low stock remaining'],
      ['info', 'Your session will expire soon'],
    ] as const)('should dispatch an ecommerce:toast event for %s', (type, message) => {
      const listener = vi.fn()
      window.addEventListener('ecommerce:toast', listener)

      toast[type](message)

      expect(listener).toHaveBeenCalledOnce()
      const event = listener.mock.calls[0][0] as CustomEvent
      expect(event.detail).toEqual({ type, message, duration: undefined })

      window.removeEventListener('ecommerce:toast', listener)
    })

    it('should include an explicit duration in the event detail when provided', () => {
      const listener = vi.fn()
      window.addEventListener('ecommerce:toast', listener)

      toast.success('Saved', 5000)

      const event = listener.mock.calls[0][0] as CustomEvent
      expect(event.detail).toEqual({ type: 'success', message: 'Saved', duration: 5000 })

      window.removeEventListener('ecommerce:toast', listener)
    })

    it('should not fall back to console when the host is mounted', () => {
      const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {})
      toast.success('Added to cart')
      expect(infoSpy).not.toHaveBeenCalled()
    })
  })

  describe('when the shell toast host is not mounted (standalone dev)', () => {
    it('should fall back to console.info for success/warning/info so the message is not lost', () => {
      const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {})
      const listener = vi.fn()
      window.addEventListener('ecommerce:toast', listener)

      toast.success('Added to cart')
      toast.warning('Low stock')
      toast.info('Heads up')

      expect(infoSpy).toHaveBeenCalledTimes(3)
      expect(listener).not.toHaveBeenCalled()

      window.removeEventListener('ecommerce:toast', listener)
    })

    it('should fall back to console.error for error toasts so the message is not lost', () => {
      const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      const listener = vi.fn()
      window.addEventListener('ecommerce:toast', listener)

      toast.error('Request failed. Please try again.')

      expect(errorSpy).toHaveBeenCalledOnce()
      expect(listener).not.toHaveBeenCalled()

      window.removeEventListener('ecommerce:toast', listener)
    })
  })
})
