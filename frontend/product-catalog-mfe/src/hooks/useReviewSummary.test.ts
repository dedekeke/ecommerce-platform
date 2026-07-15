import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { useReviewSummary } from './useReviewSummary'
import { reviewService } from '../api'

vi.mock('../api', () => ({
  reviewService: {
    getSummary: vi.fn(),
  },
}))

const mockGetSummary = vi.mocked(reviewService.getSummary)

const SUMMARY_A = { averageRating: 4.2, count: 10, distribution: { '5': 6, '4': 4 } }

describe('useReviewSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should skip fetch and set isLoading false when productId is undefined', async () => {
    const { result } = renderHook(() => useReviewSummary(undefined))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(mockGetSummary).not.toHaveBeenCalled()
    expect(result.current.summary).toBeNull()
  })

  it('should fetch and return the summary on success', async () => {
    mockGetSummary.mockResolvedValueOnce(SUMMARY_A as never)

    const { result } = renderHook(() => useReviewSummary('prod-1'))

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.summary).toEqual(SUMMARY_A)
    expect(result.current.isError).toBe(false)
  })

  it('should set isError when the fetch fails', async () => {
    mockGetSummary.mockRejectedValueOnce(new Error('network error'))

    const { result } = renderHook(() => useReviewSummary('prod-1'))

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.isError).toBe(true)
    expect(result.current.summary).toBeNull()
  })

  it('should refetch on demand', async () => {
    mockGetSummary
      .mockResolvedValueOnce(SUMMARY_A as never)
      .mockResolvedValueOnce({ ...SUMMARY_A, count: 11 } as never)

    const { result } = renderHook(() => useReviewSummary('prod-1'))
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    act(() => {
      result.current.refetch()
    })
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.summary?.count).toBe(11)
    expect(mockGetSummary).toHaveBeenCalledTimes(2)
  })
})
