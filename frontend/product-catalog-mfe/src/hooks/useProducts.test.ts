import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { useProducts } from './useProducts'
import { productService } from '../api'

vi.mock('../api', () => ({
  productService: {
    getProducts: vi.fn(),
  },
  categoryService: {},
}))

const mockGetProducts = vi.mocked(productService.getProducts)

const PAGE_1 = { content: [{ id: '1' }], totalElements: 1, totalPages: 1 }
const PAGE_2 = { content: [{ id: '2' }], totalElements: 1, totalPages: 1 }

describe('useProducts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should start with isLoading true and resolve on success', async () => {
    mockGetProducts.mockResolvedValueOnce(PAGE_1 as never)

    const { result } = renderHook(() => useProducts())

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.products).toEqual(PAGE_1.content)
    expect(result.current.isError).toBe(false)
  })

  it('should set isError when fetch fails', async () => {
    mockGetProducts.mockRejectedValueOnce(new Error('network error'))

    const { result } = renderHook(() => useProducts())

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.isError).toBe(true)
    expect(result.current.products).toEqual([])
  })

  it('should reset isLoading to true when params change (FETCH_START regression)', async () => {
    mockGetProducts
      .mockResolvedValueOnce(PAGE_1 as never)
      .mockResolvedValueOnce(PAGE_2 as never)

    const { result, rerender } = renderHook(
      (params: { page?: number }) => useProducts(params),
      { initialProps: { page: 0 } }
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.products).toEqual(PAGE_1.content)

    // Change params — isLoading must immediately reset to true before the next
    // fetch settles. Without FETCH_START this would stay false throughout.
    rerender({ page: 1 })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.products).toEqual(PAGE_2.content)
  })

  it('should reset isLoading to true on manual refetch', async () => {
    mockGetProducts
      .mockResolvedValueOnce(PAGE_1 as never)
      .mockResolvedValueOnce(PAGE_2 as never)

    const { result } = renderHook(() => useProducts())

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    act(() => {
      result.current.refetch()
    })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.products).toEqual(PAGE_2.content)
  })

  it('should not fetch when enabled is false', () => {
    const { result } = renderHook(() => useProducts({}, { enabled: false }))

    expect(result.current.isLoading).toBe(false)
    expect(result.current.products).toEqual([])
    expect(mockGetProducts).not.toHaveBeenCalled()
  })

  it('should start fetching once re-enabled', async () => {
    mockGetProducts.mockResolvedValueOnce(PAGE_1 as never)

    const { result, rerender } = renderHook(
      ({ enabled }: { enabled: boolean }) => useProducts({}, { enabled }),
      { initialProps: { enabled: false } },
    )

    expect(mockGetProducts).not.toHaveBeenCalled()

    rerender({ enabled: true })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.products).toEqual(PAGE_1.content)
  })
})
