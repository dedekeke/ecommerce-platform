import { useState, useEffect, useRef } from 'react'
import { productService } from '../api'
import type { Product, ProductSearchParams } from '../types'

interface UseProductsResult {
  products: Product[]
  totalElements: number
  totalPages: number
  isLoading: boolean
  isError: boolean
  error: Error | null
  refetch: () => void
}

export function useProducts(params: ProductSearchParams = {}): UseProductsResult {
  const [products, setProducts] = useState<Product[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [isError, setIsError] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const [fetchTrigger, setFetchTrigger] = useState(0)

  // Serialize params to detect changes
  const paramsKey = JSON.stringify(params)
  const paramsRef = useRef(params)
  const prevParamsKeyRef = useRef<string>('')

  // Update params ref when paramsKey changes
  if (paramsKey !== prevParamsKeyRef.current) {
    paramsRef.current = params
    prevParamsKeyRef.current = paramsKey
  }

  useEffect(() => {
    let cancelled = false

    const fetchProducts = async () => {
      setIsLoading(true)
      setIsError(false)
      setError(null)

      try {
        const response = await productService.getProducts(paramsRef.current)
        if (!cancelled) {
          setProducts(response.content)
          setTotalElements(response.totalElements)
          setTotalPages(response.totalPages)
        }
      } catch (err) {
        if (!cancelled) {
          setIsError(true)
          setError(err instanceof Error ? err : new Error('Failed to fetch products'))
          setProducts([])
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    fetchProducts()

    return () => {
      cancelled = true
    }
  }, [paramsKey, fetchTrigger])

  const refetch = () => setFetchTrigger((prev) => prev + 1)

  return {
    products,
    totalElements,
    totalPages,
    isLoading,
    isError,
    error,
    refetch,
  }
}

export default useProducts
