import apiClient from '../apiClient'
import type { Product, ProductSearchParams, PaginatedResponse, Category } from '../../types'

export const productService = {
  async getProducts(params: ProductSearchParams = {}): Promise<PaginatedResponse<Product>> {
    const response = await apiClient.get<PaginatedResponse<Product>>('/products', { params })
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

  async getFeaturedProducts(limit = 10): Promise<Product[]> {
    const response = await apiClient.get<Product[]>('/products/featured', {
      params: { size: limit },
    })
    return response.data
  },

  async searchProducts(
    query: string,
    params: Omit<ProductSearchParams, 'search'> = {}
  ): Promise<PaginatedResponse<Product>> {
    return this.getProducts({ ...params, search: query })
  },
}

export const categoryService = {
  async getCategories(activeOnly = true): Promise<Category[]> {
    const response = await apiClient.get<Category[]>('/v1/categories', {
      params: { activeOnly },
    })
    return response.data
  },

  async getRootCategories(includeChildren = true): Promise<Category[]> {
    const response = await apiClient.get<Category[]>('/v1/categories/root', {
      params: { includeChildren },
    })
    return response.data
  },

  async getCategoryById(id: string, includeChildren = true): Promise<Category> {
    const response = await apiClient.get<Category>(`/v1/categories/${id}`, {
      params: { includeChildren },
    })
    return response.data
  },

  async getCategoryBySlug(slug: string, includeChildren = true): Promise<Category> {
    const response = await apiClient.get<Category>(`/v1/categories/slug/${slug}`, {
      params: { includeChildren },
    })
    return response.data
  },
}

export default productService
