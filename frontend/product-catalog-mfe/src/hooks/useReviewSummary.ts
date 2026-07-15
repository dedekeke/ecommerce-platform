import { useReducer, useEffect, useCallback } from 'react'
import { reviewService } from '../api'
import type { ReviewSummary } from '../types'

interface UseReviewSummaryResult {
  summary: ReviewSummary | null
  isLoading: boolean
  isError: boolean
  refetch: () => void
}

interface SummaryState {
  summary: ReviewSummary | null
  isLoading: boolean
  isError: boolean
  fetchId: number
}

type SummaryAction =
  | { type: 'FETCH_START' }
  | { type: 'FETCH_SUCCESS'; payload: ReviewSummary }
  | { type: 'FETCH_ERROR' }
  | { type: 'FETCH_SKIP' }
  | { type: 'REFETCH' }

function reducer(state: SummaryState, action: SummaryAction): SummaryState {
  switch (action.type) {
    case 'FETCH_START':
      return { ...state, isLoading: true, isError: false }
    case 'REFETCH':
      return { ...state, isLoading: true, isError: false, fetchId: state.fetchId + 1 }
    case 'FETCH_SUCCESS':
      return { ...state, summary: action.payload, isLoading: false }
    case 'FETCH_ERROR':
      return { ...state, summary: null, isLoading: false, isError: true }
    case 'FETCH_SKIP':
      return { ...state, isLoading: false }
  }
}

export function useReviewSummary(productId: string | undefined): UseReviewSummaryResult {
  const [state, dispatch] = useReducer(reducer, {
    summary: null,
    isLoading: true,
    isError: false,
    fetchId: 0,
  })

  useEffect(() => {
    if (!productId) {
      dispatch({ type: 'FETCH_SKIP' })
      return
    }
    let cancelled = false
    dispatch({ type: 'FETCH_START' })
    const run = async () => {
      try {
        const data = await reviewService.getSummary(productId)
        if (!cancelled) dispatch({ type: 'FETCH_SUCCESS', payload: data })
      } catch {
        if (!cancelled) dispatch({ type: 'FETCH_ERROR' })
      }
    }
    void run()
    return () => {
      cancelled = true
    }
  }, [productId, state.fetchId])

  const refetch = useCallback(() => dispatch({ type: 'REFETCH' }), [])

  return {
    summary: state.summary,
    isLoading: state.isLoading,
    isError: state.isError,
    refetch,
  }
}

export default useReviewSummary
