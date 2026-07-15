import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { useProductReviews } from './useProductReviews'
import { reviewService } from '../api'

vi.mock('../api', () => ({
  reviewService: {
    listByProduct: vi.fn(),
  },
}))

const mockListByProduct = vi.mocked(reviewService.listByProduct)

const REVIEW_A = {
  id: 'r1',
  productId: 'p1',
  userId: 'u1',
  rating: 5,
  title: 'Great',
  body: 'Loved it',
  verified: true,
  helpful: 3,
  createdAt: '2026-01-01T00:00:00Z',
}

function pageOf(reviews: unknown[], page = 0, totalPages = 1) {
  return {
    content: reviews,
    page,
    size: 5,
    totalElements: reviews.length,
    totalPages,
    sort: 'helpful',
  }
}

describe('useProductReviews', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should not fetch when productId is undefined', async () => {
    const { result } = renderHook(() => useProductReviews(undefined))
    await waitFor(() => expect(result.current.isLoading).toBe(true))
    expect(mockListByProduct).not.toHaveBeenCalled()
  })

  it('should fetch page 0 on mount', async () => {
    mockListByProduct.mockResolvedValueOnce(pageOf([REVIEW_A]) as never)

    const { result } = renderHook(() => useProductReviews('p1'))
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(mockListByProduct).toHaveBeenCalledWith('p1', 0, 5)
    expect(result.current.reviews).toEqual([REVIEW_A])
    expect(result.current.totalElements).toBe(1)
  })

  it('should refetch the next page when setPage is called', async () => {
    mockListByProduct
      .mockResolvedValueOnce(pageOf([REVIEW_A], 0, 2) as never)
      .mockResolvedValueOnce(pageOf([], 1, 2) as never)

    const { result } = renderHook(() => useProductReviews('p1'))
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    act(() => {
      result.current.setPage(1)
    })

    await waitFor(() => expect(mockListByProduct).toHaveBeenCalledWith('p1', 1, 5))
  })

  it('should set isError and clear reviews when the fetch fails', async () => {
    mockListByProduct.mockRejectedValueOnce(new Error('boom'))

    const { result } = renderHook(() => useProductReviews('p1'))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.isError).toBe(true)
    expect(result.current.reviews).toEqual([])
  })

  it('should refetch the current page on demand', async () => {
    mockListByProduct
      .mockResolvedValueOnce(pageOf([REVIEW_A]) as never)
      .mockResolvedValueOnce(pageOf([REVIEW_A, REVIEW_A]) as never)

    const { result } = renderHook(() => useProductReviews('p1'))
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    act(() => {
      result.current.refetch()
    })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.reviews).toHaveLength(2)
    expect(mockListByProduct).toHaveBeenCalledTimes(2)
  })
})
