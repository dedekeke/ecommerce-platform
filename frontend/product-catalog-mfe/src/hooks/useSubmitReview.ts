import { useCallback, useState } from 'react'
import { isAxiosError } from 'axios'
import { reviewService } from '../api'
import type { CreateReviewPayload, Review } from '../types'

interface UseSubmitReviewResult {
  submitReview: (payload: CreateReviewPayload) => Promise<Review>
  isSubmitting: boolean
  error: string | null
  resetError: () => void
}

const DEFAULT_ERROR = 'Failed to submit your review. Please try again.'

/** Wraps `reviewService.createReview` — the caller decides how to refresh the list/summary on success. */
export function useSubmitReview(): UseSubmitReviewResult {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submitReview = useCallback(async (payload: CreateReviewPayload): Promise<Review> => {
    setIsSubmitting(true)
    setError(null)
    try {
      const review = await reviewService.createReview(payload)
      setIsSubmitting(false)
      return review
    } catch (err) {
      const message = isAxiosError<{ message?: string }>(err)
        ? err.response?.data?.message ?? DEFAULT_ERROR
        : DEFAULT_ERROR
      setError(message)
      setIsSubmitting(false)
      throw err
    }
  }, [])

  const resetError = useCallback(() => setError(null), [])

  return { submitReview, isSubmitting, error, resetError }
}

export default useSubmitReview
