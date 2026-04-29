import { describe, it, expect } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useCartSync } from './useCartSync'

describe('useCartSync', () => {
  it('should return synced=false when not authenticated (stub behavior)', () => {
    const { result } = renderHook(() => useCartSync(false))
    expect(result.current.synced).toBe(false)
    expect(result.current.syncing).toBe(false)
  })

  it('should return synced=false and not sync when authenticated (stub — Day 40)', () => {
    const { result } = renderHook(() => useCartSync(true))
    expect(result.current.synced).toBe(false)
    expect(result.current.syncing).toBe(false)
  })
})
