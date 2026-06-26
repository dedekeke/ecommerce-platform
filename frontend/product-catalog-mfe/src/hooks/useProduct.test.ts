import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { useProduct } from './useProduct'
import { productService } from '../api'

vi.mock('../api', () => ({
  productService: {
    getProductById: vi.fn(),
  },
  categoryService: {},
}))

const mockGetProductById = vi.mocked(productService.getProductById)

const PRODUCT_A = { id: 'a', name: 'Alpha' }
const PRODUCT_B = { id: 'b', name: 'Beta' }

describe('useProduct', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should skip fetch and set isLoading false when productId is undefined', async () => {
    const { result } = renderHook(() => useProduct(undefined))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(mockGetProductById).not.toHaveBeenCalled()
    expect(result.current.product).toBeNull()
  })

  it('should fetch and return product on success', async () => {
    mockGetProductById.mockResolvedValueOnce(PRODUCT_A as never)

    const { result } = renderHook(() => useProduct('a'))

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.product).toEqual(PRODUCT_A)
    expect(result.current.isError).toBe(false)
  })

  it('should set isError when fetch fails', async () => {
    mockGetProductById.mockRejectedValueOnce(new Error('not found'))

    const { result } = renderHook(() => useProduct('a'))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.isError).toBe(true)
    expect(result.current.product).toBeNull()
  })

  it('should reset isLoading to true when productId changes (FETCH_START regression)', async () => {
    mockGetProductById
      .mockResolvedValueOnce(PRODUCT_A as never)
      .mockResolvedValueOnce(PRODUCT_B as never)

    const { result, rerender } = renderHook(
      (id: string) => useProduct(id),
      { initialProps: 'a' }
    )

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.product).toEqual(PRODUCT_A)

    // Switch to a different product — isLoading must reset immediately.
    rerender('b')
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.product).toEqual(PRODUCT_B)
  })

  it('should reset isLoading to true on manual refetch', async () => {
    mockGetProductById
      .mockResolvedValueOnce(PRODUCT_A as never)
      .mockResolvedValueOnce(PRODUCT_A as never)

    const { result } = renderHook(() => useProduct('a'))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    act(() => {
      result.current.refetch()
    })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
  })
})
