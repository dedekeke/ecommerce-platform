/**
 * Mirrors review-service DTOs (ReviewResponse / ReviewSummaryResponse) as seen
 * through the API gateway's `/api/v1/reviews/**` route.
 */
export interface Review {
  id: string
  productId: string
  userId: string
  rating: number
  title: string
  body: string
  verified: boolean
  helpful: number
  createdAt: string
}

/** distribution is keyed by rating (1-5); Jackson serializes Map<Integer, Long> keys as strings. */
export interface ReviewSummary {
  averageRating: number
  count: number
  distribution: Record<string, number>
}

export interface ReviewPage {
  content: Review[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  sort: string
}

export interface CreateReviewPayload {
  productId: string
  rating: number
  title: string
  body: string
}
