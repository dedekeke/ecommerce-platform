import { describe, it, expect } from 'vitest'
import { isMockAuthMode } from './mockAuth'

describe('isMockAuthMode', () => {
  it('should return true when running on the dev server with VITE_AUTH_MODE=mock', () => {
    expect(isMockAuthMode({ DEV: true, VITE_AUTH_MODE: 'mock' })).toBe(true)
  })

  it('should return false in a production build even when VITE_AUTH_MODE=mock (runtime safety guard)', () => {
    expect(isMockAuthMode({ DEV: false, VITE_AUTH_MODE: 'mock' })).toBe(false)
  })

  it('should return false when VITE_AUTH_MODE is unset', () => {
    expect(isMockAuthMode({ DEV: true, VITE_AUTH_MODE: undefined })).toBe(false)
  })

  it('should return false for any value other than the exact string "mock"', () => {
    expect(isMockAuthMode({ DEV: true, VITE_AUTH_MODE: 'Mock' })).toBe(false)
    expect(isMockAuthMode({ DEV: true, VITE_AUTH_MODE: 'true' })).toBe(false)
    expect(isMockAuthMode({ DEV: true, VITE_AUTH_MODE: '' })).toBe(false)
  })

  it('should default to reading import.meta.env (mock inactive under vitest without the env var)', () => {
    expect(isMockAuthMode()).toBe(false)
  })
})
