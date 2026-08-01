import { useReducer, useEffect, useCallback } from 'react'
import { productService } from '../api'
import type { Product, ProductSearchParams } from '../types'

interface UseProductsOptions {
  enabled?: boolean
}

interface UseProductsResult {
  products: Product[]
  totalElements: number
  totalPages: number
  isLoading: boolean
  isError: boolean
  error: Error | null
  refetch: () => void
}

interface ProductsState {
  products: Product[]
  totalElements: number
  totalPages: number
  isLoading: boolean
  isError: boolean
  error: Error | null
  fetchId: number
}

type ProductsAction =
  | { type: 'FETCH_START' }
  | { type: 'FETCH_SUCCESS'; payload: { content: Product[]; totalElements: number; totalPages: number } }
  | { type: 'FETCH_ERROR'; payload: Error }
  | { type: 'DISABLED' }
  | { type: 'REFETCH' }

function reducer(state: ProductsState, action: ProductsAction): ProductsState {
  switch (action.type) {
    case 'FETCH_START':
      return { ...state, isLoading: true, isError: false, error: null }
    case 'REFETCH':
      return { ...state, isLoading: true, isError: false, error: null, fetchId: state.fetchId + 1 }
    case 'FETCH_SUCCESS':
      return {
        ...state,
        products: action.payload.content,
        totalElements: action.payload.totalElements,
        totalPages: action.payload.totalPages,
        isLoading: false,
      }
    case 'FETCH_ERROR':
      return { ...state, products: [], isLoading: false, isError: true, error: action.payload }
    case 'DISABLED':
      return { ...state, products: [], totalElements: 0, isLoading: false, isError: false, error: null }
  }
}

export function useProducts(
  params: ProductSearchParams = {},
  options: UseProductsOptions = {},
): UseProductsResult {
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

  // Stable serialized key drives re-fetch when params change.
  const paramsKey = JSON.stringify(params)

  useEffect(() => {
    if (!enabled) {
      dispatch({ type: 'DISABLED' })
      return
    }

    let cancelled = false
    dispatch({ type: 'FETCH_START' })
    const currentParams: ProductSearchParams = JSON.parse(paramsKey) as ProductSearchParams
    const run = async () => {
      try {
        const response = await productService.getProducts(currentParams)
        if (!cancelled)
          dispatch({
            type: 'FETCH_SUCCESS',
            payload: {
              content: response.content,
              totalElements: response.totalElements,
              totalPages: response.totalPages,
            },
          })
      } catch (err) {
        if (!cancelled)
          dispatch({
            type: 'FETCH_ERROR',
            payload: err instanceof Error ? err : new Error('Failed to fetch products'),
          })
      }
    }
    void run()
    return () => { cancelled = true }
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

export default useProducts
