import { useMemo } from 'react'
import { useShallow } from 'zustand/shallow'
import { Box, Typography, Breadcrumbs, Link, Container } from '@mui/material'
import { Home as HomeIcon } from '@mui/icons-material'
import { ProductGrid } from '../components/product'
import { Pagination } from '../components/common'
import { SortDropdown, FilterPanel } from '../components/filters'
import { ProductSearch } from '../components/search'
import { useProducts, useSearchResults, useCategories } from '../hooks'
import { useFilterUrlSync } from '../hooks/useFilterUrlSync'
import { useProductFilterStore } from '../stores'
import { resolveCategoryName } from '../utils/resolveCategoryName'
import type { ProductSearchParams, SearchProductsParams } from '../types'

interface ProductListPageProps {
  onAddToCart?: (productId: string, quantity: number) => void
  currency?: string
}

export default function ProductListPage({
  onAddToCart,
  currency = 'USD',
}: ProductListPageProps) {
  useFilterUrlSync()

  const filters = useProductFilterStore(
    useShallow((state) => ({
      categoryId: state.categoryId,
      minPrice: state.minPrice,
      maxPrice: state.maxPrice,
      inStockOnly: state.inStockOnly,
      sortBy: state.sortBy,
      sortDirection: state.sortDirection,
      page: state.page,
      size: state.size,
      searchQuery: state.searchQuery,
    }))
  )

  const { setPage, setSize, setSort, setSearchQuery, setCategory, setPriceRange, setInStockOnly } =
    useProductFilterStore()

  const { categories, isLoading: categoriesLoading } = useCategories(true)

  const isSearchMode = Boolean(filters.searchQuery)

  const browseParams = useMemo<ProductSearchParams>(() => {
    const params: ProductSearchParams = {
      page: filters.page,
      size: filters.size,
      sortBy: filters.sortBy,
      sortDirection: filters.sortDirection,
      activeOnly: true,
    }
    if (filters.categoryId) params.categoryId = filters.categoryId
    if (filters.minPrice !== null) params.minPrice = filters.minPrice
    if (filters.maxPrice !== null) params.maxPrice = filters.maxPrice
    if (filters.inStockOnly) params.inStockOnly = true
    return params
  }, [filters.page, filters.size, filters.sortBy, filters.sortDirection, filters.categoryId, filters.minPrice, filters.maxPrice, filters.inStockOnly])

  // search-service filters by category *name*, not by the slug/id productFilterStore holds.
  const categoryName = useMemo(
    () => resolveCategoryName(categories, filters.categoryId),
    [categories, filters.categoryId]
  )

  const searchApiParams = useMemo<SearchProductsParams>(() => {
    const params: SearchProductsParams = {
      q: filters.searchQuery,
      page: filters.page,
      size: filters.size,
    }
    if (categoryName) params.categories = [categoryName]
    if (filters.minPrice !== null) params.minPrice = filters.minPrice
    if (filters.maxPrice !== null) params.maxPrice = filters.maxPrice
    return params
  }, [filters.searchQuery, categoryName, filters.minPrice, filters.maxPrice, filters.page, filters.size])

  const browseResult = useProducts(browseParams, { enabled: !isSearchMode })
  const searchResult = useSearchResults(searchApiParams, { enabled: isSearchMode })
  const active = isSearchMode ? searchResult : browseResult

  // search-service has no inStockOnly param — applied client-side as a documented limitation.
  const products =
    isSearchMode && filters.inStockOnly ? active.products.filter((product) => product.inStock) : active.products

  const handleClearFilters = () => {
    setCategory(null)
    setPriceRange(null, null)
    setInStockOnly(false)
  }

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      <Breadcrumbs sx={{ mb: 3 }}>
        <Link
          href="/"
          color="inherit"
          sx={{ display: 'flex', alignItems: 'center' }}
          underline="hover"
        >
          <HomeIcon sx={{ mr: 0.5, fontSize: 20 }} />
          Home
        </Link>
        <Typography color="text.primary">Products</Typography>
      </Breadcrumbs>

      <Box sx={{ mb: 3 }}>
        <ProductSearch initialQuery={filters.searchQuery} onSearch={setSearchQuery} />
      </Box>

      <Box sx={{ display: 'flex', gap: 4, alignItems: 'flex-start', flexDirection: { xs: 'column', md: 'row' } }}>
        <Box sx={{ width: { xs: '100%', md: 280 }, flexShrink: 0 }}>
          <FilterPanel
            categories={categories}
            categoriesLoading={categoriesLoading}
            categoryId={filters.categoryId}
            minPrice={filters.minPrice}
            maxPrice={filters.maxPrice}
            inStockOnly={filters.inStockOnly}
            onCategoryChange={setCategory}
            onPriceChange={setPriceRange}
            onInStockChange={setInStockOnly}
            onClear={handleClearFilters}
          />
        </Box>

        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Box
            sx={{
              display: 'flex',
              flexDirection: { xs: 'column', sm: 'row' },
              justifyContent: 'space-between',
              alignItems: { xs: 'flex-start', sm: 'center' },
              gap: 2,
              mb: 3,
            }}
          >
            <Box>
              <Typography variant="h4" fontWeight={700}>
                {isSearchMode ? `Search: "${filters.searchQuery}"` : 'All Products'}
              </Typography>
              {!active.isLoading && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                  {products.length} {products.length === 1 ? 'product' : 'products'} found
                </Typography>
              )}
            </Box>

            <SortDropdown
              sortBy={filters.sortBy}
              sortDirection={filters.sortDirection}
              onSortChange={setSort}
              disabled={isSearchMode}
            />
          </Box>

          {active.isError ? (
            <Box sx={{ textAlign: 'center', py: 8 }}>
              <Typography variant="h6" color="error" gutterBottom>
                Failed to load products
              </Typography>
              <Typography color="text.secondary">Please try again later</Typography>
            </Box>
          ) : (
            <>
              <ProductGrid
                products={products}
                isLoading={active.isLoading}
                onAddToCart={onAddToCart}
                currency={currency}
                emptyMessage={
                  isSearchMode
                    ? `No products found for "${filters.searchQuery}"`
                    : 'No products found'
                }
              />

              {!active.isLoading && active.totalPages > 1 && (
                <Pagination
                  page={filters.page}
                  totalPages={active.totalPages}
                  totalElements={active.totalElements}
                  pageSize={filters.size}
                  onPageChange={setPage}
                  onPageSizeChange={setSize}
                />
              )}
            </>
          )}
        </Box>
      </Box>
    </Container>
  )
}
