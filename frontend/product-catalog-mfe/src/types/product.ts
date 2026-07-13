export interface ProductDimensions {
  length: number
  width: number
  height: number
  weight: number
}

export interface CategoryResponse {
  id: string
  name: string
  slug: string
  description?: string
  parentId?: string
  parentName?: string
  children?: CategoryResponse[]
  imageUrl?: string
  active: boolean
  displayOrder: number
  level: number
  fullPath: string
  hasChildren: boolean
}

export interface ProductResponse {
  id: string
  sku: string
  name: string
  description: string
  category: CategoryResponse | null
  price: number
  currency: string
  images: string[]
  dimensions?: ProductDimensions
  stockQuantity: number
  active: boolean
  inStock: boolean
  available: boolean
  createdAt: string
  updatedAt: string
}

export interface ProductSearchParams {
  search?: string
  categoryId?: string
  minPrice?: number
  maxPrice?: number
  activeOnly?: boolean
  inStockOnly?: boolean
  page?: number
  size?: number
  sortBy?: 'name' | 'price' | 'createdAt'
  sortDirection?: 'asc' | 'desc'
}

export interface PaginatedResponse<T> {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  numberOfElements?: number
  empty?: boolean
}

export type Product = ProductResponse
export type Category = CategoryResponse
