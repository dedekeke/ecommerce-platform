import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useColorMode } from './useColorMode'
import { useUserPreferencesStore } from '../stores/userPreferencesStore'

vi.mock('@mui/material/styles', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@mui/material/styles')>()
  return { ...actual }
})

const mockMatchMedia = (matches: boolean) => {
  return vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))
}

describe('useColorMode', () => {
  beforeEach(() => {
    useUserPreferencesStore.getState().resetPreferences()
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: mockMatchMedia(false),
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    document.documentElement.classList.remove('light', 'dark')
  })

  it('should resolve to light mode by default', () => {
    const { result } = renderHook(() => useColorMode())
    expect(result.current.resolvedMode).toBe('light')
  })

  it('should resolve to dark mode when preference is dark', () => {
    useUserPreferencesStore.getState().setTheme('dark')
    const { result } = renderHook(() => useColorMode())
    expect(result.current.resolvedMode).toBe('dark')
  })

  it('should apply light theme class to html element', () => {
    const { result } = renderHook(() => useColorMode())
    expect(result.current.resolvedMode).toBe('light')
    expect(document.documentElement.classList.contains('light')).toBe(true)
  })

  it('should apply dark theme class to html element when dark mode', () => {
    useUserPreferencesStore.getState().setTheme('dark')
    renderHook(() => useColorMode())
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('should toggle from light to dark', () => {
    const { result } = renderHook(() => useColorMode())
    expect(result.current.resolvedMode).toBe('light')
    act(() => {
      result.current.toggle()
    })
    expect(useUserPreferencesStore.getState().theme).toBe('dark')
  })

  it('should toggle from dark to light', () => {
    useUserPreferencesStore.getState().setTheme('dark')
    const { result } = renderHook(() => useColorMode())
    act(() => {
      result.current.toggle()
    })
    expect(useUserPreferencesStore.getState().theme).toBe('light')
  })

  it('should use system preference when theme is system and system is dark', () => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: mockMatchMedia(true),
    })
    useUserPreferencesStore.getState().setTheme('system')
    const { result } = renderHook(() => useColorMode())
    expect(result.current.resolvedMode).toBe('dark')
  })

  it('should use system preference when theme is system and system is light', () => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: mockMatchMedia(false),
    })
    useUserPreferencesStore.getState().setTheme('system')
    const { result } = renderHook(() => useColorMode())
    expect(result.current.resolvedMode).toBe('light')
  })

  it('should return a muiTheme object', () => {
    const { result } = renderHook(() => useColorMode())
    expect(result.current.muiTheme).toBeDefined()
    expect(result.current.muiTheme.palette).toBeDefined()
  })

  it('should return dark muiTheme when mode is dark', () => {
    useUserPreferencesStore.getState().setTheme('dark')
    const { result } = renderHook(() => useColorMode())
    expect(result.current.muiTheme.palette.mode).toBe('dark')
  })

  it('should return light muiTheme when mode is light', () => {
    useUserPreferencesStore.getState().setTheme('light')
    const { result } = renderHook(() => useColorMode())
    expect(result.current.muiTheme.palette.mode).toBe('light')
  })

  it('should expose preference from the store', () => {
    useUserPreferencesStore.getState().setTheme('dark')
    const { result } = renderHook(() => useColorMode())
    expect(result.current.preference).toBe('dark')
  })
})
