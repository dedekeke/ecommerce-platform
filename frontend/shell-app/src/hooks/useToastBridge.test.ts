import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useToastBridge, TOAST_EVENT } from './useToastBridge'
import { useNotificationStore } from '../stores'

function dispatchToast(detail: unknown) {
  act(() => {
    window.dispatchEvent(new CustomEvent(TOAST_EVENT, { detail }))
  })
}

describe('useToastBridge', () => {
  beforeEach(() => {
    useNotificationStore.setState({ notifications: [] })
    delete window.__ecommerceToastHost
  })

  afterEach(() => {
    delete window.__ecommerceToastHost
  })

  it('should set window.__ecommerceToastHost to true on mount', () => {
    renderHook(() => useToastBridge())
    expect(window.__ecommerceToastHost).toBe(true)
  })

  it('should remove window.__ecommerceToastHost on unmount', () => {
    const { unmount } = renderHook(() => useToastBridge())
    unmount()
    expect(window.__ecommerceToastHost).toBeUndefined()
  })

  it('should add a notification when a valid toast event is dispatched', () => {
    renderHook(() => useToastBridge())
    dispatchToast({ type: 'success', message: 'Item added to cart' })
    const notifications = useNotificationStore.getState().notifications
    expect(notifications).toHaveLength(1)
    expect(notifications[0]).toMatchObject({ type: 'success', message: 'Item added to cart' })
  })

  it('should pass through an explicit duration', () => {
    renderHook(() => useToastBridge())
    dispatchToast({ type: 'info', message: 'Synced', duration: 2000 })
    expect(useNotificationStore.getState().notifications[0].duration).toBe(2000)
  })

  it('should ignore an event with a non-string message', () => {
    renderHook(() => useToastBridge())
    dispatchToast({ type: 'error', message: 42 })
    expect(useNotificationStore.getState().notifications).toHaveLength(0)
  })

  it('should ignore an event with an empty message', () => {
    renderHook(() => useToastBridge())
    dispatchToast({ type: 'error', message: '   ' })
    expect(useNotificationStore.getState().notifications).toHaveLength(0)
  })

  it('should ignore an event with an unknown type', () => {
    renderHook(() => useToastBridge())
    dispatchToast({ type: 'critical', message: 'Something happened' })
    expect(useNotificationStore.getState().notifications).toHaveLength(0)
  })

  it('should ignore an event with a missing detail', () => {
    renderHook(() => useToastBridge())
    dispatchToast(undefined)
    expect(useNotificationStore.getState().notifications).toHaveLength(0)
  })

  it('should ignore an event with a non-numeric duration', () => {
    renderHook(() => useToastBridge())
    dispatchToast({ type: 'warning', message: 'Low stock', duration: 'fast' })
    expect(useNotificationStore.getState().notifications).toHaveLength(0)
  })

  it('should stop listening after unmount', () => {
    const { unmount } = renderHook(() => useToastBridge())
    unmount()
    dispatchToast({ type: 'success', message: 'Too late' })
    expect(useNotificationStore.getState().notifications).toHaveLength(0)
  })

  it('should export the event name constant', () => {
    expect(TOAST_EVENT).toBe('ecommerce:toast')
  })

  describe('dev-mode observability', () => {
    let warnSpy: ReturnType<typeof vi.spyOn>

    beforeEach(() => {
      warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    })
    afterEach(() => {
      warnSpy.mockRestore()
    })

    it('should console.warn a malformed payload in dev mode', () => {
      renderHook(() => useToastBridge())
      dispatchToast({ type: 'critical', message: 'Something happened' })

      if (import.meta.env.DEV) {
        expect(warnSpy).toHaveBeenCalledWith('[toast] ignored malformed payload', {
          type: 'critical',
          message: 'Something happened',
        })
      }
    })

    it('should not warn for a valid payload', () => {
      renderHook(() => useToastBridge())
      dispatchToast({ type: 'success', message: 'All good' })

      expect(warnSpy).not.toHaveBeenCalled()
    })
  })
})
