import { useReducer, useEffect, useCallback } from 'react'
import { categoryService } from '../api'
import type { Category } from '../types'

interface UseCategoriesResult {
  categories: Category[]
  isLoading: boolean
  isError: boolean
  error: Error | null
  refetch: () => void
}

interface CategoriesState {
  categories: Category[]
  isLoading: boolean
  isError: boolean
  error: Error | null
  fetchId: number
}

type CategoriesAction =
  | { type: 'FETCH_START' }
  | { type: 'FETCH_SUCCESS'; payload: Category[] }
  | { type: 'FETCH_ERROR'; payload: Error }
  | { type: 'REFETCH' }

function reducer(state: CategoriesState, action: CategoriesAction): CategoriesState {
  switch (action.type) {
    case 'FETCH_START':
      return { ...state, isLoading: true, isError: false, error: null }
    case 'REFETCH':
      return { ...state, isLoading: true, isError: false, error: null, fetchId: state.fetchId + 1 }
    case 'FETCH_SUCCESS':
      return { ...state, categories: action.payload, isLoading: false }
    case 'FETCH_ERROR':
      return { ...state, categories: [], isLoading: false, isError: true, error: action.payload }
  }
}

export function useCategories(rootOnly = true): UseCategoriesResult {
  const [state, dispatch] = useReducer(reducer, {
    categories: [],
    isLoading: true,
    isError: false,
    error: null,
    fetchId: 0,
  })

  useEffect(() => {
    let cancelled = false
    dispatch({ type: 'FETCH_START' })
    const run = async () => {
      try {
        const data = rootOnly
          ? await categoryService.getRootCategories()
          : await categoryService.getCategories()
        if (!cancelled) dispatch({ type: 'FETCH_SUCCESS', payload: data })
      } catch (err) {
        if (!cancelled)
          dispatch({
            type: 'FETCH_ERROR',
            payload: err instanceof Error ? err : new Error('Failed to fetch categories'),
          })
      }
    }
    void run()
    return () => { cancelled = true }
  }, [state.fetchId, rootOnly])

  const refetch = useCallback(() => dispatch({ type: 'REFETCH' }), [])

  return {
    categories: state.categories,
    isLoading: state.isLoading,
    isError: state.isError,
    error: state.error,
    refetch,
  }
}

export default useCategories
