import { describe, it, expect, afterEach, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useAuthUserId, ANONYMOUS_USER_PREFIX } from './useAuthUserId'

afterEach(() => {
  delete window.__getAuthUserId
  vi.restoreAllMocks()
})

describe('useAuthUserId', () => {
  it('should return the authenticated user id exposed by the shell', () => {
    window.__getAuthUserId = () => 'auth0|abc123'
    const { result } = renderHook(() => useAuthUserId())
    expect(result.current).toBe('auth0|abc123')
  })

  it('should fall back to a clearly-marked anonymous id when not authenticated', () => {
    window.__getAuthUserId = () => null
    const { result } = renderHook(() => useAuthUserId())
    expect(result.current.startsWith(ANONYMOUS_USER_PREFIX)).toBe(true)
  })

  it('should fall back to an anonymous id when the shell accessor is absent', () => {
    delete window.__getAuthUserId
    const { result } = renderHook(() => useAuthUserId())
    expect(result.current.startsWith(ANONYMOUS_USER_PREFIX)).toBe(true)
  })

  it('should never return the legacy hardcoded "guest" value', () => {
    delete window.__getAuthUserId
    const { result } = renderHook(() => useAuthUserId())
    expect(result.current).not.toBe('guest')
  })

  it('should keep a stable anonymous id across re-renders', () => {
    delete window.__getAuthUserId
    const { result, rerender } = renderHook(() => useAuthUserId())
    const first = result.current
    rerender()
    expect(result.current).toBe(first)
  })
})
