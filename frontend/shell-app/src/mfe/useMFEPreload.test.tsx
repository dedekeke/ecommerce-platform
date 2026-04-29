import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useMFEPreload } from './useMFEPreload'

const mockPreloadModule = vi.fn()

vi.mock('./moduleLoader', () => ({
  preloadModule: (...args: unknown[]) => mockPreloadModule(...args),
  isModulePreloaded: vi.fn(() => false),
}))

describe('useMFEPreload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  describe('Basic Functionality', () => {
    it('should return preload handlers', () => {
      const { result } = renderHook(() => useMFEPreload('productCatalog'))

      expect(result.current.onMouseEnter).toBeDefined()
      expect(result.current.onMouseLeave).toBeDefined()
      expect(result.current.onFocus).toBeDefined()
      expect(result.current.onBlur).toBeDefined()
    })

    it('should return preload status', () => {
      const { result } = renderHook(() => useMFEPreload('productCatalog'))

      expect(result.current.isPreloading).toBe(false)
      expect(result.current.isPreloaded).toBe(false)
    })

    it('should return preload function for manual triggering', () => {
      const { result } = renderHook(() => useMFEPreload('productCatalog'))

      expect(result.current.preload).toBeDefined()
      expect(typeof result.current.preload).toBe('function')
    })
  })

  describe('Mouse Events', () => {
    it('should preload on mouse enter after delay', () => {
      const { result } = renderHook(() =>
        useMFEPreload('productCatalog', { delay: 100 })
      )

      act(() => {
        result.current.onMouseEnter()
      })

      expect(mockPreloadModule).not.toHaveBeenCalled()

      act(() => {
        vi.advanceTimersByTime(100)
      })

      expect(mockPreloadModule).toHaveBeenCalledWith('productCatalog')
    })

    it('should not preload if mouse leaves before delay', () => {
      const { result } = renderHook(() =>
        useMFEPreload('productCatalog', { delay: 100 })
      )

      act(() => {
        result.current.onMouseEnter()
      })

      act(() => {
        vi.advanceTimersByTime(50)
      })

      act(() => {
        result.current.onMouseLeave()
      })

      act(() => {
        vi.advanceTimersByTime(100)
      })

      expect(mockPreloadModule).not.toHaveBeenCalled()
    })

    it('should use default delay of 150ms', () => {
      const { result } = renderHook(() => useMFEPreload('productCatalog'))

      act(() => {
        result.current.onMouseEnter()
      })

      act(() => {
        vi.advanceTimersByTime(149)
      })

      expect(mockPreloadModule).not.toHaveBeenCalled()

      act(() => {
        vi.advanceTimersByTime(1)
      })

      expect(mockPreloadModule).toHaveBeenCalledWith('productCatalog')
    })
  })

  describe('Focus Events', () => {
    it('should preload on focus after delay', () => {
      const { result } = renderHook(() =>
        useMFEPreload('productCatalog', { delay: 100 })
      )

      act(() => {
        result.current.onFocus()
      })

      expect(mockPreloadModule).not.toHaveBeenCalled()

      act(() => {
        vi.advanceTimersByTime(100)
      })

      expect(mockPreloadModule).toHaveBeenCalledWith('productCatalog')
    })

    it('should not preload if blur happens before delay', () => {
      const { result } = renderHook(() =>
        useMFEPreload('productCatalog', { delay: 100 })
      )

      act(() => {
        result.current.onFocus()
      })

      act(() => {
        vi.advanceTimersByTime(50)
      })

      act(() => {
        result.current.onBlur()
      })

      act(() => {
        vi.advanceTimersByTime(100)
      })

      expect(mockPreloadModule).not.toHaveBeenCalled()
    })
  })

  describe('Manual Preload', () => {
    it('should preload immediately when preload() is called', () => {
      const { result } = renderHook(() => useMFEPreload('productCatalog'))

      act(() => {
        result.current.preload()
      })

      expect(mockPreloadModule).toHaveBeenCalledWith('productCatalog')
    })

    it('should not preload twice', () => {
      const { result } = renderHook(() => useMFEPreload('productCatalog'))

      act(() => {
        result.current.preload()
      })

      act(() => {
        result.current.preload()
      })

      expect(mockPreloadModule).toHaveBeenCalledTimes(1)
    })
  })

  describe('Preload Status', () => {
    it('should update isPreloading during preload', () => {
      const { result } = renderHook(() => useMFEPreload('productCatalog'))

      expect(result.current.isPreloading).toBe(false)

      act(() => {
        result.current.onMouseEnter()
        vi.advanceTimersByTime(150)
      })

      expect(result.current.isPreloaded).toBe(true)
    })
  })

  describe('Different MFE Names', () => {
    it('should preload the correct MFE', () => {
      const { result: result1 } = renderHook(() => useMFEPreload('cart'))
      const { result: result2 } = renderHook(() => useMFEPreload('checkout'))

      act(() => {
        result1.current.preload()
        result2.current.preload()
      })

      expect(mockPreloadModule).toHaveBeenCalledWith('cart')
      expect(mockPreloadModule).toHaveBeenCalledWith('checkout')
    })
  })

  describe('Cleanup', () => {
    it('should cleanup timer on unmount', () => {
      const { result, unmount } = renderHook(() =>
        useMFEPreload('productCatalog', { delay: 100 })
      )

      act(() => {
        result.current.onMouseEnter()
      })

      unmount()

      act(() => {
        vi.advanceTimersByTime(200)
      })

      expect(mockPreloadModule).not.toHaveBeenCalled()
    })
  })

  describe('Options', () => {
    it('should accept delay option', () => {
      const { result } = renderHook(() =>
        useMFEPreload('productCatalog', { delay: 500 })
      )

      act(() => {
        result.current.onMouseEnter()
      })

      act(() => {
        vi.advanceTimersByTime(499)
      })

      expect(mockPreloadModule).not.toHaveBeenCalled()

      act(() => {
        vi.advanceTimersByTime(1)
      })

      expect(mockPreloadModule).toHaveBeenCalled()
    })

    it('should accept priority option', () => {
      renderHook(() =>
        useMFEPreload('productCatalog', { priority: 'high' })
      )

      // Priority is currently a placeholder for future implementation
      // Just ensure no errors occur
    })
  })
})
