import apiClient from '../apiClient'
import type { CreateReviewPayload, Review, ReviewPage, ReviewSummary } from '../../types'

/**
 * Thin wrapper around the review-service API, reached through the gateway's
 * versioned `/api/v1/reviews/**` route (rewritten to `/api/reviews/**` at the
 * gateway before hitting review-service on :8093). Read endpoints are
 * anonymous; `createReview` requires the Authorization header the shared
 * `apiClient` interceptor attaches from `window.__getAuthToken`.
 */
export const reviewService = {
  async getSummary(productId: string): Promise<ReviewSummary> {
    const response = await apiClient.get<ReviewSummary>(
      `/v1/reviews/product/${productId}/summary`,
    )
    return response.data
  },

  async listByProduct(
    productId: string,
    page = 0,
    size = 5,
    sort = 'helpful',
  ): Promise<ReviewPage> {
    const response = await apiClient.get<ReviewPage>(
      `/v1/reviews/product/${productId}`,
      { params: { page, size, sort } },
    )
    return response.data
  },

  async createReview(payload: CreateReviewPayload): Promise<Review> {
    const response = await apiClient.post<Review>('/v1/reviews', payload)
    return response.data
  },
}

export default reviewService
