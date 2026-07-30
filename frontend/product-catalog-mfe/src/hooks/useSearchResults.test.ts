import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { useSearchResults } from './useSearchResults'
import { searchService } from '../api'

vi.mock('../api', () => ({
  searchService: {
    search: vi.fn(),
  },
}))

const mockSearch = vi.mocked(searchService.search)

const PAGE_1 = { products: [{ id: '1' }], totalElements: 1, totalPages: 1 }
const PAGE_2 = { products: [{ id: '2' }], totalElements: 1, totalPages: 1 }

describe('useSearchResults', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should start with isLoading true and resolve on success', async () => {
    mockSearch.mockResolvedValueOnce(PAGE_1 as never)

    const { result } = renderHook(() => useSearchResults({ q: 'shoes' }))

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.products).toEqual(PAGE_1.products)
    expect(result.current.totalElements).toBe(1)
    expect(result.current.isError).toBe(false)
  })

  it('should set isError when the search request fails', async () => {
    mockSearch.mockRejectedValueOnce(new Error('network error'))

    const { result } = renderHook(() => useSearchResults({ q: 'shoes' }))

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.isError).toBe(true)
    expect(result.current.products).toEqual([])
  })

  it('should refetch when params change', async () => {
    mockSearch
      .mockResolvedValueOnce(PAGE_1 as never)
      .mockResolvedValueOnce(PAGE_2 as never)

    const { result, rerender } = renderHook(
      (params: { q: string }) => useSearchResults(params),
      { initialProps: { q: 'shoes' } },
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.products).toEqual(PAGE_1.products)

    rerender({ q: 'hats' })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.products).toEqual(PAGE_2.products)
    expect(mockSearch).toHaveBeenNthCalledWith(2, { q: 'hats' })
  })

  it('should reset isLoading to true on manual refetch', async () => {
    mockSearch
      .mockResolvedValueOnce(PAGE_1 as never)
      .mockResolvedValueOnce(PAGE_2 as never)

    const { result } = renderHook(() => useSearchResults({ q: 'shoes' }))
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    act(() => {
      result.current.refetch()
    })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.products).toEqual(PAGE_2.products)
  })

  it('should not fetch when enabled is false', () => {
    const { result } = renderHook(() => useSearchResults({ q: 'shoes' }, { enabled: false }))

    expect(result.current.isLoading).toBe(false)
    expect(result.current.products).toEqual([])
    expect(mockSearch).not.toHaveBeenCalled()
  })

  it('should start fetching once re-enabled', async () => {
    mockSearch.mockResolvedValueOnce(PAGE_1 as never)

    const { result, rerender } = renderHook(
      ({ enabled }: { enabled: boolean }) => useSearchResults({ q: 'shoes' }, { enabled }),
      { initialProps: { enabled: false } },
    )

    expect(mockSearch).not.toHaveBeenCalled()

    rerender({ enabled: true })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.products).toEqual(PAGE_1.products)
  })
})
