import { describe, it, expect, afterEach, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useAuthUserId } from './useAuthUserId'

afterEach(() => {
  delete window.__getAuthUserId
  vi.restoreAllMocks()
})

describe('useAuthUserId', () => {
  it('should return the authenticated user id (Auth0 sub) exposed by the shell', () => {
    window.__getAuthUserId = () => 'auth0|abc123'
    const { result } = renderHook(() => useAuthUserId())
    expect(result.current).toBe('auth0|abc123')
  })

  it('should return null when the shell reports no authenticated user', () => {
    window.__getAuthUserId = () => null
    const { result } = renderHook(() => useAuthUserId())
    expect(result.current).toBeNull()
  })

  it('should return null when the shell accessor is absent (standalone dev)', () => {
    delete window.__getAuthUserId
    const { result } = renderHook(() => useAuthUserId())
    expect(result.current).toBeNull()
  })

  it('should never return the legacy hardcoded "guest" value', () => {
    delete window.__getAuthUserId
    const { result } = renderHook(() => useAuthUserId())
    expect(result.current).not.toBe('guest')
  })

  it('should keep a stable value across re-renders', () => {
    window.__getAuthUserId = () => 'auth0|abc123'
    const { result, rerender } = renderHook(() => useAuthUserId())
    const first = result.current
    rerender()
    expect(result.current).toBe(first)
  })
})
