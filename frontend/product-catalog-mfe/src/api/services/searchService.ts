import apiClient from '../apiClient'
import type { Product, SearchProductsParams, SearchProductsResult } from '../../types'

/** Raw shape of a search-service ProductDocument (Elasticsearch hit). */
interface SearchProductDocument {
  id: string
  name: string
  description?: string
  sku?: string
  category?: string
  price: number
  currency?: string
  images?: string[]
  active?: boolean
  stockQuantity?: number
  createdAt?: string
  updatedAt?: string
}

interface FacetedSearchResponse {
  products: SearchProductDocument[]
  totalElements: number
}

interface AutocompleteResponse {
  suggestions: string[]
}

const EPOCH = new Date(0).toISOString()

/** Maps a search-service ProductDocument onto the Product shape ProductGrid/ProductCard expect. */
export function mapSearchDocumentToProduct(doc: SearchProductDocument): Product {
  const stockQuantity = doc.stockQuantity ?? 0
  const active = doc.active ?? true

  return {
    id: doc.id,
    sku: doc.sku ?? '',
    name: doc.name,
    description: doc.description ?? '',
    category: doc.category
      ? {
          id: doc.category,
          name: doc.category,
          slug: doc.category.toLowerCase().replace(/\s+/g, '-'),
          active: true,
          displayOrder: 0,
          level: 0,
          fullPath: doc.category,
          hasChildren: false,
        }
      : null,
    price: doc.price,
    currency: doc.currency ?? 'USD',
    images: doc.images ?? [],
    stockQuantity,
    active,
    inStock: stockQuantity > 0,
    available: active && stockQuantity > 0,
    createdAt: doc.createdAt ?? EPOCH,
    updatedAt: doc.updatedAt ?? EPOCH,
  }
}

export const searchService = {
  /** Search-as-you-type suggestions. Empty/whitespace queries short-circuit without a request. */
  async autocomplete(query: string): Promise<string[]> {
    if (!query.trim()) return []

    const response = await apiClient.get<AutocompleteResponse>('/search/autocomplete', {
      params: { query },
      // Autocomplete is a nice-to-have — a failed suggestion fetch shouldn't toast over the user's typing.
      skipErrorToast: true,
    })
    return response.data.suggestions ?? []
  },

  async search(params: SearchProductsParams = {}): Promise<SearchProductsResult> {
    const response = await apiClient.get<FacetedSearchResponse>('/search', {
      params: {
        q: params.q,
        categories: params.categories?.length ? params.categories.join(',') : undefined,
        minPrice: params.minPrice,
        maxPrice: params.maxPrice,
        page: params.page,
        size: params.size,
      },
    })

    const size = params.size ?? 20
    const totalElements = response.data.totalElements ?? 0

    return {
      products: (response.data.products ?? []).map(mapSearchDocumentToProduct),
      totalElements,
      totalPages: size > 0 ? Math.ceil(totalElements / size) : 0,
    }
  },
}

export default searchService
