import { useState, useEffect, useCallback } from 'react'
import { productService } from '../api'
import type { Product } from '../types'

interface UseProductResult {
  product: Product | null
  isLoading: boolean
  isError: boolean
  error: Error | null
  refetch: () => void
}

export function useProduct(productId: string | undefined): UseProductResult {
  const [product, setProduct] = useState<Product | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isError, setIsError] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  const fetchProduct = useCallback(async () => {
    if (!productId) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setIsError(false)
    setError(null)

    try {
      const data = await productService.getProductById(productId)
      setProduct(data)
    } catch (err) {
      setIsError(true)
      setError(err instanceof Error ? err : new Error('Failed to fetch product'))
      setProduct(null)
    } finally {
      setIsLoading(false)
    }
  }, [productId])

  useEffect(() => {
    fetchProduct()
  }, [fetchProduct])

  return {
    product,
    isLoading,
    isError,
    error,
    refetch: fetchProduct,
  }
}

export default useProduct
