import { useEffect, useRef } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { useShallow } from 'zustand/shallow'
import { useProductFilterStore } from '../stores'

interface UrlFilterState {
  searchQuery: string
  categoryId: string | null
  minPrice: number | null
  maxPrice: number | null
  inStockOnly: boolean
}

function parseUrlFilters(searchParams: URLSearchParams, categorySlug?: string): UrlFilterState {
  const minPriceParam = searchParams.get('minPrice')
  const maxPriceParam = searchParams.get('maxPrice')
  const minPrice = minPriceParam !== null && minPriceParam !== '' ? Number(minPriceParam) : null
  const maxPrice = maxPriceParam !== null && maxPriceParam !== '' ? Number(maxPriceParam) : null

  return {
    searchQuery: searchParams.get('q') ?? '',
    categoryId: searchParams.get('category') ?? categorySlug ?? null,
    minPrice: minPrice !== null && Number.isNaN(minPrice) ? null : minPrice,
    maxPrice: maxPrice !== null && Number.isNaN(maxPrice) ? null : maxPrice,
    inStockOnly: searchParams.get('inStock') === 'true',
  }
}

function filtersToParams(filters: UrlFilterState, base: URLSearchParams): URLSearchParams {
  const next = new URLSearchParams(base)

  if (filters.searchQuery) next.set('q', filters.searchQuery)
  else next.delete('q')

  if (filters.categoryId) next.set('category', filters.categoryId)
  else next.delete('category')

  if (filters.minPrice !== null) next.set('minPrice', String(filters.minPrice))
  else next.delete('minPrice')

  if (filters.maxPrice !== null) next.set('maxPrice', String(filters.maxPrice))
  else next.delete('maxPrice')

  if (filters.inStockOnly) next.set('inStock', 'true')
  else next.delete('inStock')

  return next
}

function sameFilters(a: UrlFilterState, b: UrlFilterState): boolean {
  return (
    a.searchQuery === b.searchQuery &&
    a.categoryId === b.categoryId &&
    a.minPrice === b.minPrice &&
    a.maxPrice === b.maxPrice &&
    a.inStockOnly === b.inStockOnly
  )
}

/**
 * Two-way sync between productFilterStore and the URL query string, so filtered/searched
 * views are shareable (per the "URL is the source of truth" convention).
 *
 * A naive "URL -> store" effect plus a "store -> url" effect race each other: each reacts to
 * the other's *previous* render's value, which can oscillate indefinitely instead of settling.
 * Tracking the last state we ourselves synced (in a ref) lets a single effect tell "the URL
 * changed externally" apart from "the store changed locally" and apply exactly one direction
 * per real change.
 */
export function useFilterUrlSync(): void {
  const [searchParams, setSearchParams] = useSearchParams()
  const { categorySlug } = useParams()

  const filters = useProductFilterStore(
    useShallow((state) => ({
      searchQuery: state.searchQuery,
      categoryId: state.categoryId,
      minPrice: state.minPrice,
      maxPrice: state.maxPrice,
      inStockOnly: state.inStockOnly,
    }))
  )
  const setSearchQuery = useProductFilterStore((state) => state.setSearchQuery)
  const setCategory = useProductFilterStore((state) => state.setCategory)
  const setPriceRange = useProductFilterStore((state) => state.setPriceRange)
  const setInStockOnly = useProductFilterStore((state) => state.setInStockOnly)

  const lastSyncedRef = useRef<UrlFilterState | null>(null)

  useEffect(() => {
    const urlFilters = parseUrlFilters(searchParams, categorySlug)
    const last = lastSyncedRef.current

    if (!last || !sameFilters(urlFilters, last)) {
      // URL changed (or this is the initial mount) — hydrate the store from it.
      if (urlFilters.searchQuery !== filters.searchQuery) setSearchQuery(urlFilters.searchQuery)
      if (urlFilters.categoryId !== filters.categoryId) setCategory(urlFilters.categoryId)
      if (urlFilters.minPrice !== filters.minPrice || urlFilters.maxPrice !== filters.maxPrice) {
        setPriceRange(urlFilters.minPrice, urlFilters.maxPrice)
      }
      if (urlFilters.inStockOnly !== filters.inStockOnly) setInStockOnly(urlFilters.inStockOnly)
      lastSyncedRef.current = urlFilters
      return
    }

    if (!sameFilters(filters, last)) {
      // URL is unchanged since our last sync, so this render was triggered by a store change
      // (e.g. FilterPanel/ProductSearch) — push it to the URL.
      const next = filtersToParams(filters, searchParams)
      if (next.toString() !== searchParams.toString()) {
        setSearchParams(next, { replace: true })
      }
      lastSyncedRef.current = filters
    }
  }, [
    searchParams,
    categorySlug,
    filters,
    setSearchQuery,
    setCategory,
    setPriceRange,
    setInStockOnly,
    setSearchParams,
  ])
}

export default useFilterUrlSync
