import { apiClient } from '../apiClient'
import type {
  Product,
  Category,
  PaginatedResponse,
  ProductSearchParams,
} from '../types'

export const productService = {
  async getProducts(
    params: ProductSearchParams = {}
  ): Promise<PaginatedResponse<Product>> {
    const response = await apiClient.get<PaginatedResponse<Product>>(
      '/products',
      { params }
    )
    return response.data
  },

  async getProductById(id: string): Promise<Product> {
    const response = await apiClient.get<Product>(`/products/${id}`)
    return response.data
  },

  async getProductBySku(sku: string): Promise<Product> {
    const response = await apiClient.get<Product>(`/products/sku/${sku}`)
    return response.data
  },

  async searchProducts(
    query: string,
    params: Omit<ProductSearchParams, 'query'> = {}
  ): Promise<PaginatedResponse<Product>> {
    const response = await apiClient.get<PaginatedResponse<Product>>(
      '/products/search',
      {
        params: { q: query, ...params },
      }
    )
    return response.data
  },

  async getProductsByCategory(
    categoryId: string,
    page?: number,
    size?: number
  ): Promise<PaginatedResponse<Product>> {
    const params: ProductSearchParams = { categoryId }
    if (page !== undefined) params.page = page
    if (size !== undefined) params.size = size

    const response = await apiClient.get<PaginatedResponse<Product>>(
      '/products',
      { params }
    )
    return response.data
  },

  async getFeaturedProducts(limit?: number): Promise<Product[]> {
    const config = limit ? { params: { limit } } : undefined
    const response = await apiClient.get<Product[]>(
      '/products/featured',
      config
    )
    return response.data
  },

  async getCategories(): Promise<Category[]> {
    const response = await apiClient.get<Category[]>('/categories')
    return response.data
  },

  async getCategoryById(id: string): Promise<Category> {
    const response = await apiClient.get<Category>(`/categories/${id}`)
    return response.data
  },

  async getCategoryBySlug(slug: string): Promise<Category> {
    const response = await apiClient.get<Category>(`/categories/slug/${slug}`)
    return response.data
  },

  async getSubcategories(parentId: string): Promise<Category[]> {
    const response = await apiClient.get<Category[]>(
      `/categories/${parentId}/subcategories`
    )
    return response.data
  },
}

export default productService
