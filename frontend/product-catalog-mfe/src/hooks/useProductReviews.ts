import { useReducer, useEffect, useCallback } from 'react'
import { reviewService } from '../api'
import type { Review } from '../types'

interface UseProductReviewsResult {
  reviews: Review[]
  page: number
  totalPages: number
  totalElements: number
  isLoading: boolean
  isError: boolean
  setPage: (page: number) => void
  refetch: () => void
}

interface ReviewsState {
  reviews: Review[]
  page: number
  totalPages: number
  totalElements: number
  isLoading: boolean
  isError: boolean
  fetchId: number
}

type ReviewsAction =
  | { type: 'FETCH_START' }
  | {
      type: 'FETCH_SUCCESS'
      payload: { content: Review[]; page: number; totalElements: number; totalPages: number }
    }
  | { type: 'FETCH_ERROR' }
  | { type: 'SET_PAGE'; payload: number }
  | { type: 'REFETCH' }

function reducer(state: ReviewsState, action: ReviewsAction): ReviewsState {
  switch (action.type) {
    case 'FETCH_START':
      return { ...state, isLoading: true, isError: false }
    case 'REFETCH':
      return { ...state, isLoading: true, isError: false, fetchId: state.fetchId + 1 }
    case 'SET_PAGE':
      return { ...state, page: action.payload }
    case 'FETCH_SUCCESS':
      return {
        ...state,
        reviews: action.payload.content,
        page: action.payload.page,
        totalElements: action.payload.totalElements,
        totalPages: action.payload.totalPages,
        isLoading: false,
      }
    case 'FETCH_ERROR':
      return { ...state, reviews: [], isLoading: false, isError: true }
  }
}

/** Reviews are sorted "helpful first" — matches the review-service default. */
export function useProductReviews(
  productId: string | undefined,
  pageSize = 5,
): UseProductReviewsResult {
  const [state, dispatch] = useReducer(reducer, {
    reviews: [],
    page: 0,
    totalPages: 0,
    totalElements: 0,
    isLoading: true,
    isError: false,
    fetchId: 0,
  })

  useEffect(() => {
    if (!productId) {
      return
    }
    let cancelled = false
    dispatch({ type: 'FETCH_START' })
    const run = async () => {
      try {
        const data = await reviewService.listByProduct(productId, state.page, pageSize)
        if (!cancelled)
          dispatch({
            type: 'FETCH_SUCCESS',
            payload: {
              content: data.content,
              page: data.page,
              totalElements: data.totalElements,
              totalPages: data.totalPages,
            },
          })
      } catch {
        if (!cancelled) dispatch({ type: 'FETCH_ERROR' })
      }
    }
    void run()
    return () => {
      cancelled = true
    }
  }, [productId, pageSize, state.page, state.fetchId])

  const setPage = useCallback((page: number) => dispatch({ type: 'SET_PAGE', payload: page }), [])
  const refetch = useCallback(() => dispatch({ type: 'REFETCH' }), [])

  return {
    reviews: state.reviews,
    page: state.page,
    totalPages: state.totalPages,
    totalElements: state.totalElements,
    isLoading: state.isLoading,
    isError: state.isError,
    setPage,
    refetch,
  }
}

export default useProductReviews
