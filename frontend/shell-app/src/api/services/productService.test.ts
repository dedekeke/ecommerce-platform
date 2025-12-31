import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { productService } from './productService'
import { apiClient } from '../apiClient'
import type { Product, Category, ProductSearchParams } from '../types'

vi.mock('../apiClient', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockProduct: Product = {
  id: 'prod-1',
  sku: 'SKU001',
  name: 'Test Product',
  description: 'A test product description',
  price: 99.99,
  currency: 'USD',
  category: {
    id: 'cat-1',
    name: 'Electronics',
    slug: 'electronics',
  },
  images: ['image1.jpg', 'image2.jpg'],
  stockQuantity: 100,
  active: true,
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
}

const mockProducts: Product[] = [
  mockProduct,
  {
    ...mockProduct,
    id: 'prod-2',
    sku: 'SKU002',
    name: 'Another Product',
    price: 149.99,
  },
]

const mockPaginatedResponse = {
  content: mockProducts,
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
  first: true,
  last: true,
}

const mockCategories: Category[] = [
  { id: 'cat-1', name: 'Electronics', slug: 'electronics' },
  { id: 'cat-2', name: 'Clothing', slug: 'clothing', parentId: undefined },
]

describe('ProductService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getProducts', () => {
    it('should fetch products with default parameters', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockPaginatedResponse })

      const result = await productService.getProducts()

      expect(apiClient.get).toHaveBeenCalledWith('/products', { params: {} })
      expect(result).toEqual(mockPaginatedResponse)
    })

    it('should fetch products with search parameters', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockPaginatedResponse })

      const params: ProductSearchParams = {
        query: 'test',
        categoryId: 'cat-1',
        minPrice: 50,
        maxPrice: 200,
        page: 0,
        size: 20,
        sort: 'price,asc',
      }

      const result = await productService.getProducts(params)

      expect(apiClient.get).toHaveBeenCalledWith('/products', { params })
      expect(result).toEqual(mockPaginatedResponse)
    })

    it('should handle empty results', async () => {
      const emptyResponse = {
        ...mockPaginatedResponse,
        content: [],
        totalElements: 0,
        totalPages: 0,
      }
      vi.mocked(apiClient.get).mockResolvedValue({ data: emptyResponse })

      const result = await productService.getProducts({ query: 'nonexistent' })

      expect(result.content).toHaveLength(0)
      expect(result.totalElements).toBe(0)
    })
  })

  describe('getProductById', () => {
    it('should fetch a single product by ID', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockProduct })

      const result = await productService.getProductById('prod-1')

      expect(apiClient.get).toHaveBeenCalledWith('/products/prod-1')
      expect(result).toEqual(mockProduct)
    })

    it('should throw error for non-existent product', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('Product not found'))

      await expect(productService.getProductById('invalid')).rejects.toThrow(
        'Product not found'
      )
    })
  })

  describe('getProductBySku', () => {
    it('should fetch a product by SKU', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockProduct })

      const result = await productService.getProductBySku('SKU001')

      expect(apiClient.get).toHaveBeenCalledWith('/products/sku/SKU001')
      expect(result).toEqual(mockProduct)
    })
  })

  describe('searchProducts', () => {
    it('should search products with query', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockPaginatedResponse })

      const result = await productService.searchProducts('test')

      expect(apiClient.get).toHaveBeenCalledWith('/products/search', {
        params: { q: 'test' },
      })
      expect(result).toEqual(mockPaginatedResponse)
    })

    it('should search products with additional params', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockPaginatedResponse })

      const result = await productService.searchProducts('test', {
        categoryId: 'cat-1',
        page: 1,
      })

      expect(apiClient.get).toHaveBeenCalledWith('/products/search', {
        params: { q: 'test', categoryId: 'cat-1', page: 1 },
      })
      expect(result).toEqual(mockPaginatedResponse)
    })
  })

  describe('getProductsByCategory', () => {
    it('should fetch products by category ID', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockPaginatedResponse })

      const result = await productService.getProductsByCategory('cat-1')

      expect(apiClient.get).toHaveBeenCalledWith('/products', {
        params: { categoryId: 'cat-1' },
      })
      expect(result).toEqual(mockPaginatedResponse)
    })

    it('should fetch products by category with pagination', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockPaginatedResponse })

      const result = await productService.getProductsByCategory('cat-1', 2, 10)

      expect(apiClient.get).toHaveBeenCalledWith('/products', {
        params: { categoryId: 'cat-1', page: 2, size: 10 },
      })
      expect(result).toEqual(mockPaginatedResponse)
    })
  })

  describe('getFeaturedProducts', () => {
    it('should fetch featured products', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockProducts })

      const result = await productService.getFeaturedProducts()

      expect(apiClient.get).toHaveBeenCalledWith('/products/featured', undefined)
      expect(result).toEqual(mockProducts)
    })

    it('should fetch featured products with limit', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockProducts })

      const result = await productService.getFeaturedProducts(5)

      expect(apiClient.get).toHaveBeenCalledWith('/products/featured', {
        params: { limit: 5 },
      })
      expect(result).toEqual(mockProducts)
    })
  })

  describe('getCategories', () => {
    it('should fetch all categories', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCategories })

      const result = await productService.getCategories()

      expect(apiClient.get).toHaveBeenCalledWith('/categories')
      expect(result).toEqual(mockCategories)
    })
  })

  describe('getCategoryById', () => {
    it('should fetch a category by ID', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCategories[0] })

      const result = await productService.getCategoryById('cat-1')

      expect(apiClient.get).toHaveBeenCalledWith('/categories/cat-1')
      expect(result).toEqual(mockCategories[0])
    })
  })

  describe('getCategoryBySlug', () => {
    it('should fetch a category by slug', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCategories[0] })

      const result = await productService.getCategoryBySlug('electronics')

      expect(apiClient.get).toHaveBeenCalledWith('/categories/slug/electronics')
      expect(result).toEqual(mockCategories[0])
    })
  })

  describe('getSubcategories', () => {
    it('should fetch subcategories for a parent category', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockCategories })

      const result = await productService.getSubcategories('cat-1')

      expect(apiClient.get).toHaveBeenCalledWith('/categories/cat-1/subcategories')
      expect(result).toEqual(mockCategories)
    })
  })
})
