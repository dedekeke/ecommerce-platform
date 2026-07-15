import { describe, it, expect, afterEach, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useAuthUserId } from './useAuthUserId'

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

  it('should pick up the accessor when the shell installs it after mount', () => {
    delete window.__getAuthUserId
    const { result, rerender } = renderHook(() => useAuthUserId())
    expect(result.current).toBeNull()
    window.__getAuthUserId = () => 'auth0|late-arrival'
    rerender()
    expect(result.current).toBe('auth0|late-arrival')
  })
})
