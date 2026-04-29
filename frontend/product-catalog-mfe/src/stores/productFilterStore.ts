import { create } from 'zustand'
import { devtools } from 'zustand/middleware'
import type { ProductSearchParams } from '../types'

interface ProductFilterState {
  categoryId: string | null
  minPrice: number | null
  maxPrice: number | null
  inStockOnly: boolean
  sortBy: 'name' | 'price' | 'createdAt'
  sortDirection: 'asc' | 'desc'
  page: number
  size: number
  searchQuery: string

  setCategory: (categoryId: string | null) => void
  setPriceRange: (min: number | null, max: number | null) => void
  setInStockOnly: (value: boolean) => void
  setSort: (sortBy: 'name' | 'price' | 'createdAt', direction: 'asc' | 'desc') => void
  setPage: (page: number) => void
  setSize: (size: number) => void
  setSearchQuery: (query: string) => void
  resetFilters: () => void
  getSearchParams: () => ProductSearchParams
}

const initialState = {
  categoryId: null,
  minPrice: null,
  maxPrice: null,
  inStockOnly: false,
  sortBy: 'createdAt' as const,
  sortDirection: 'desc' as const,
  page: 0,
  size: 12,
  searchQuery: '',
}

export const useProductFilterStore = create<ProductFilterState>()(
  devtools(
    (set, get) => ({
      ...initialState,

      setCategory: (categoryId) => set({ categoryId, page: 0 }),
      setPriceRange: (minPrice, maxPrice) => set({ minPrice, maxPrice, page: 0 }),
      setInStockOnly: (inStockOnly) => set({ inStockOnly, page: 0 }),
      setSort: (sortBy, sortDirection) => set({ sortBy, sortDirection }),
      setPage: (page) => set({ page }),
      setSize: (size) => set({ size, page: 0 }),
      setSearchQuery: (searchQuery) => set({ searchQuery, page: 0 }),
      resetFilters: () => set(initialState),

      getSearchParams: () => {
        const state = get()
        const params: ProductSearchParams = {
          page: state.page,
          size: state.size,
          sortBy: state.sortBy,
          sortDirection: state.sortDirection,
          activeOnly: true,
        }

        if (state.searchQuery) params.search = state.searchQuery
        if (state.categoryId) params.categoryId = state.categoryId
        if (state.minPrice !== null) params.minPrice = state.minPrice
        if (state.maxPrice !== null) params.maxPrice = state.maxPrice
        if (state.inStockOnly) params.inStockOnly = true

        return params
      },
    }),
    { name: 'product-filter-store' }
  )
)

export const selectFilters = (state: ProductFilterState) => ({
  categoryId: state.categoryId,
  minPrice: state.minPrice,
  maxPrice: state.maxPrice,
  inStockOnly: state.inStockOnly,
  sortBy: state.sortBy,
  sortDirection: state.sortDirection,
  page: state.page,
  size: state.size,
  searchQuery: state.searchQuery,
})

export const selectPagination = (state: ProductFilterState) => ({
  page: state.page,
  size: state.size,
})

export default useProductFilterStore
