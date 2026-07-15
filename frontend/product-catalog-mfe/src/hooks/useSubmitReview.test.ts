import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { AxiosError } from 'axios'
import { useSubmitReview } from './useSubmitReview'
import { reviewService } from '../api'

vi.mock('../api', () => ({
  reviewService: {
    createReview: vi.fn(),
  },
}))

const mockCreateReview = vi.mocked(reviewService.createReview)

const PAYLOAD = { productId: 'p1', rating: 5, title: 'Great', body: 'Loved it' }
const REVIEW = { id: 'r1', ...PAYLOAD, userId: 'u1', verified: false, helpful: 0, createdAt: '2026-01-01T00:00:00Z' }

describe('useSubmitReview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should start with isSubmitting false and no error', () => {
    const { result } = renderHook(() => useSubmitReview())
    expect(result.current.isSubmitting).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('should set isSubmitting true while the request is in flight, then resolve with the created review', async () => {
    let resolveFn!: (value: unknown) => void
    mockCreateReview.mockImplementationOnce(
      () => new Promise((resolve) => { resolveFn = resolve }) as never,
    )

    const { result } = renderHook(() => useSubmitReview())

    let submitPromise!: Promise<unknown>
    act(() => {
      submitPromise = result.current.submitReview(PAYLOAD)
    })

    await waitFor(() => expect(result.current.isSubmitting).toBe(true))

    resolveFn(REVIEW)
    await act(async () => {
      await submitPromise
    })

    expect(result.current.isSubmitting).toBe(false)
    expect(await submitPromise).toEqual(REVIEW)
  })

  it('should surface the server-provided error message on failure', async () => {
    const axiosError = new AxiosError('Request failed')
    axiosError.response = {
      data: { message: 'Rating must be between 1 and 5' },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: {} as never,
    }
    mockCreateReview.mockRejectedValueOnce(axiosError)

    const { result } = renderHook(() => useSubmitReview())

    await act(async () => {
      await expect(result.current.submitReview(PAYLOAD)).rejects.toThrow()
    })

    expect(result.current.isSubmitting).toBe(false)
    expect(result.current.error).toBe('Rating must be between 1 and 5')
  })

  it('should fall back to a generic error message for non-axios failures', async () => {
    mockCreateReview.mockRejectedValueOnce(new Error('network down'))

    const { result } = renderHook(() => useSubmitReview())

    await act(async () => {
      await expect(result.current.submitReview(PAYLOAD)).rejects.toThrow()
    })

    expect(result.current.error).toBe('Failed to submit your review. Please try again.')
  })

  it('should clear the error via resetError', async () => {
    mockCreateReview.mockRejectedValueOnce(new Error('boom'))
    const { result } = renderHook(() => useSubmitReview())

    await act(async () => {
      await expect(result.current.submitReview(PAYLOAD)).rejects.toThrow()
    })
    expect(result.current.error).not.toBeNull()

    act(() => {
      result.current.resetError()
    })
    expect(result.current.error).toBeNull()
  })
})
