import { useReducer, useEffect, useCallback } from 'react'
import { searchService } from '../api'
import type { Product, SearchProductsParams } from '../types'

interface UseSearchResultsOptions {
  enabled?: boolean
}

interface UseSearchResultsResult {
  products: Product[]
  totalElements: number
  totalPages: number
  isLoading: boolean
  isError: boolean
  error: Error | null
  refetch: () => void
}

interface SearchResultsState {
  products: Product[]
  totalElements: number
  totalPages: number
  isLoading: boolean
  isError: boolean
  error: Error | null
  fetchId: number
}

type SearchResultsAction =
  | { type: 'FETCH_START' }
  | { type: 'FETCH_SUCCESS'; payload: { products: Product[]; totalElements: number; totalPages: number } }
  | { type: 'FETCH_ERROR'; payload: Error }
  | { type: 'DISABLED' }
  | { type: 'REFETCH' }

function reducer(state: SearchResultsState, action: SearchResultsAction): SearchResultsState {
  switch (action.type) {
    case 'FETCH_START':
      return { ...state, isLoading: true, isError: false, error: null }
    case 'REFETCH':
      return { ...state, isLoading: true, isError: false, error: null, fetchId: state.fetchId + 1 }
    case 'FETCH_SUCCESS':
      return {
        ...state,
        products: action.payload.products,
        totalElements: action.payload.totalElements,
        totalPages: action.payload.totalPages,
        isLoading: false,
      }
    case 'FETCH_ERROR':
      return { ...state, products: [], isLoading: false, isError: true, error: action.payload }
    case 'DISABLED':
      return { ...state, isLoading: false, isError: false, error: null }
  }
}

export function useSearchResults(
  params: SearchProductsParams = {},
  options: UseSearchResultsOptions = {},
): UseSearchResultsResult {
  const { enabled = true } = options
  const [state, dispatch] = useReducer(reducer, {
    products: [],
    totalElements: 0,
    totalPages: 0,
    isLoading: enabled,
    isError: false,
    error: null,
    fetchId: 0,
  })

  const paramsKey = JSON.stringify(params)

  useEffect(() => {
    if (!enabled) {
      dispatch({ type: 'DISABLED' })
      return
    }

    let cancelled = false
    dispatch({ type: 'FETCH_START' })
    const currentParams: SearchProductsParams = JSON.parse(paramsKey) as SearchProductsParams
    const run = async () => {
      try {
        const response = await searchService.search(currentParams)
        if (!cancelled)
          dispatch({
            type: 'FETCH_SUCCESS',
            payload: {
              products: response.products,
              totalElements: response.totalElements,
              totalPages: response.totalPages,
            },
          })
      } catch (err) {
        if (!cancelled)
          dispatch({
            type: 'FETCH_ERROR',
            payload: err instanceof Error ? err : new Error('Failed to fetch search results'),
          })
      }
    }
    void run()
    return () => {
      cancelled = true
    }
  }, [paramsKey, state.fetchId, enabled])

  const refetch = useCallback(() => dispatch({ type: 'REFETCH' }), [])

  return {
    products: state.products,
    totalElements: state.totalElements,
    totalPages: state.totalPages,
    isLoading: state.isLoading,
    isError: state.isError,
    error: state.error,
    refetch,
  }
}

export default useSearchResults
