import { useEffect, useReducer } from 'react'
import { searchService } from '../api'
import { useDebouncedValue } from './useDebouncedValue'

const MIN_QUERY_LENGTH = 2

interface AutocompleteState {
  suggestions: string[]
  isLoading: boolean
  isError: boolean
}

type AutocompleteAction =
  | { type: 'FETCH_START' }
  | { type: 'FETCH_SUCCESS'; payload: string[] }
  | { type: 'FETCH_ERROR' }
  | { type: 'CLEAR' }

function reducer(state: AutocompleteState, action: AutocompleteAction): AutocompleteState {
  switch (action.type) {
    case 'FETCH_START':
      return { ...state, isLoading: true, isError: false }
    case 'FETCH_SUCCESS':
      return { suggestions: action.payload, isLoading: false, isError: false }
    case 'FETCH_ERROR':
      return { suggestions: [], isLoading: false, isError: true }
    case 'CLEAR':
      return { suggestions: [], isLoading: false, isError: false }
  }
}

export function useAutocomplete(query: string, debounceMs = 300): AutocompleteState {
  const debouncedQuery = useDebouncedValue(query.trim(), debounceMs)
  const [state, dispatch] = useReducer(reducer, {
    suggestions: [],
    isLoading: false,
    isError: false,
  })

  useEffect(() => {
    if (debouncedQuery.length < MIN_QUERY_LENGTH) {
      dispatch({ type: 'CLEAR' })
      return
    }

    let cancelled = false
    dispatch({ type: 'FETCH_START' })

    searchService
      .autocomplete(debouncedQuery)
      .then((suggestions) => {
        if (!cancelled) dispatch({ type: 'FETCH_SUCCESS', payload: suggestions })
      })
      .catch(() => {
        if (!cancelled) dispatch({ type: 'FETCH_ERROR' })
      })

    return () => {
      cancelled = true
    }
  }, [debouncedQuery])

  return state
}

export default useAutocomplete
