import { useReducer, useEffect, useCallback } from 'react'
import { productService } from '../api'
import type { Product } from '../types'

interface UseProductResult {
  product: Product | null
  isLoading: boolean
  isError: boolean
  error: Error | null
  refetch: () => void
}

interface ProductState {
  product: Product | null
  isLoading: boolean
  isError: boolean
  error: Error | null
  fetchId: number
}

type ProductAction =
  | { type: 'FETCH_SUCCESS'; payload: Product }
  | { type: 'FETCH_ERROR'; payload: Error }
  | { type: 'FETCH_SKIP' }
  | { type: 'REFETCH' }

function reducer(state: ProductState, action: ProductAction): ProductState {
  switch (action.type) {
    case 'REFETCH':
      return { ...state, isLoading: true, isError: false, error: null, fetchId: state.fetchId + 1 }
    case 'FETCH_SUCCESS':
      return { ...state, product: action.payload, isLoading: false }
    case 'FETCH_ERROR':
      return { ...state, product: null, isLoading: false, isError: true, error: action.payload }
    case 'FETCH_SKIP':
      return { ...state, isLoading: false }
  }
}

export function useProduct(productId: string | undefined): UseProductResult {
  const [state, dispatch] = useReducer(reducer, {
    product: null,
    isLoading: true,
    isError: false,
    error: null,
    fetchId: 0,
  })

  useEffect(() => {
    if (!productId) {
      dispatch({ type: 'FETCH_SKIP' })
      return
    }
    let cancelled = false
    const run = async () => {
      try {
        const data = await productService.getProductById(productId)
        if (!cancelled) dispatch({ type: 'FETCH_SUCCESS', payload: data })
      } catch (err) {
        if (!cancelled)
          dispatch({
            type: 'FETCH_ERROR',
            payload: err instanceof Error ? err : new Error('Failed to fetch product'),
          })
      }
    }
    void run()
    return () => { cancelled = true }
  }, [productId, state.fetchId])

  const refetch = useCallback(() => dispatch({ type: 'REFETCH' }), [])

  return {
    product: state.product,
    isLoading: state.isLoading,
    isError: state.isError,
    error: state.error,
    refetch,
  }
}

export default useProduct
