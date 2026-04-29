import { useState, useEffect, useCallback } from 'react'
import { categoryService } from '../api'
import type { Category } from '../types'

interface UseCategoriesResult {
  categories: Category[]
  isLoading: boolean
  isError: boolean
  error: Error | null
  refetch: () => void
}

export function useCategories(rootOnly = true): UseCategoriesResult {
  const [categories, setCategories] = useState<Category[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isError, setIsError] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  const fetchCategories = useCallback(async () => {
    setIsLoading(true)
    setIsError(false)
    setError(null)

    try {
      const data = rootOnly
        ? await categoryService.getRootCategories()
        : await categoryService.getCategories()
      setCategories(data)
    } catch (err) {
      setIsError(true)
      setError(err instanceof Error ? err : new Error('Failed to fetch categories'))
      setCategories([])
    } finally {
      setIsLoading(false)
    }
  }, [rootOnly])

  useEffect(() => {
    fetchCategories()
  }, [fetchCategories])

  return {
    categories,
    isLoading,
    isError,
    error,
    refetch: fetchCategories,
  }
}

export default useCategories
