import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useAutocomplete } from './useAutocomplete'
import { searchService } from '../api'

vi.mock('../api', () => ({
  searchService: {
    autocomplete: vi.fn(),
  },
}))

const mockAutocomplete = vi.mocked(searchService.autocomplete)

/** Advances fake timers then flushes the resulting microtasks so pending promises settle. */
async function flush(ms: number) {
  await act(async () => {
    vi.advanceTimersByTime(ms)
    await Promise.resolve()
    await Promise.resolve()
  })
}

describe('useAutocomplete', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAutocomplete.mockResolvedValue([])
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should not call the API before the debounce delay elapses', async () => {
    const { rerender } = renderHook(({ query }) => useAutocomplete(query), {
      initialProps: { query: '' },
    })
    rerender({ query: 'sh' })

    await flush(299)

    expect(mockAutocomplete).not.toHaveBeenCalled()
  })

  it('should fetch suggestions once debounced and query length >= 2', async () => {
    mockAutocomplete.mockResolvedValueOnce(['shoes', 'shorts'])
    const { result, rerender } = renderHook(({ query }) => useAutocomplete(query), {
      initialProps: { query: '' },
    })
    rerender({ query: 'sh' })

    await flush(300)

    expect(mockAutocomplete).toHaveBeenCalledWith('sh')
    expect(result.current.isLoading).toBe(false)
    expect(result.current.suggestions).toEqual(['shoes', 'shorts'])
  })

  it('should not call the API for queries shorter than 2 characters', async () => {
    const { rerender } = renderHook(({ query }) => useAutocomplete(query), {
      initialProps: { query: '' },
    })
    rerender({ query: 's' })

    await flush(300)

    expect(mockAutocomplete).not.toHaveBeenCalled()
  })

  it('should clear suggestions when the query is cleared', async () => {
    mockAutocomplete.mockResolvedValueOnce(['shoes'])
    const { result, rerender } = renderHook(({ query }) => useAutocomplete(query), {
      initialProps: { query: '' },
    })
    rerender({ query: 'sh' })
    await flush(300)
    expect(result.current.suggestions).toEqual(['shoes'])

    rerender({ query: '' })
    await flush(300)

    expect(result.current.suggestions).toEqual([])
  })

  it('should set isError and clear suggestions when the request fails', async () => {
    mockAutocomplete.mockRejectedValueOnce(new Error('network error'))
    const { result, rerender } = renderHook(({ query }) => useAutocomplete(query), {
      initialProps: { query: '' },
    })
    rerender({ query: 'zz' })

    await flush(300)

    expect(result.current.isLoading).toBe(false)
    expect(result.current.isError).toBe(true)
    expect(result.current.suggestions).toEqual([])
  })

  it('should ignore a stale response when the query changes before it resolves', async () => {
    let resolveFirst: (value: string[]) => void = () => {}
    mockAutocomplete.mockImplementationOnce(
      () => new Promise((resolve) => { resolveFirst = resolve }),
    )
    mockAutocomplete.mockResolvedValueOnce(['second-result'])

    const { result, rerender } = renderHook(({ query }) => useAutocomplete(query), {
      initialProps: { query: '' },
    })

    rerender({ query: 'first' })
    await flush(300)

    rerender({ query: 'second' })
    await flush(300)
    expect(result.current.suggestions).toEqual(['second-result'])

    await act(async () => {
      resolveFirst(['stale-result'])
      await Promise.resolve()
    })

    expect(result.current.suggestions).toEqual(['second-result'])
  })
})
