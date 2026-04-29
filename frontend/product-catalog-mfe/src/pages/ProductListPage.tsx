import { useEffect, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useShallow } from 'zustand/shallow'
import { Box, Typography, Breadcrumbs, Link, Container } from '@mui/material'
import { Home as HomeIcon } from '@mui/icons-material'
import { ProductGrid } from '../components/product'
import { Pagination } from '../components/common'
import { SortDropdown } from '../components/filters'
import { useProducts } from '../hooks'
import { useProductFilterStore } from '../stores'
import type { ProductSearchParams } from '../types'

interface ProductListPageProps {
  onAddToCart?: (productId: string, quantity: number) => void
  currency?: string
}

export default function ProductListPage({
  onAddToCart,
  currency = 'USD',
}: ProductListPageProps) {
  const [searchParams] = useSearchParams()

  // Use shallow comparison to prevent unnecessary re-renders
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

  const { setPage, setSize, setSort, setSearchQuery, setCategory } = useProductFilterStore()

  useEffect(() => {
    const search = searchParams.get('q')
    const category = searchParams.get('category')
    if (search) setSearchQuery(search)
    if (category) setCategory(category)
  }, [searchParams, setSearchQuery, setCategory])

  // Memoize search params to prevent infinite re-renders
  const apiParams = useMemo<ProductSearchParams>(() => {
    const params: ProductSearchParams = {
      page: filters.page,
      size: filters.size,
      sortBy: filters.sortBy,
      sortDirection: filters.sortDirection,
      activeOnly: true,
    }
    if (filters.searchQuery) params.search = filters.searchQuery
    // todo : categoryId has to be long. currently using string causing 500
    if (filters.categoryId) params.categoryId = filters.categoryId
    if (filters.minPrice !== null) params.minPrice = filters.minPrice
    if (filters.maxPrice !== null) params.maxPrice = filters.maxPrice
    if (filters.inStockOnly) params.inStockOnly = true
    return params
  }, [filters])

  const { products, totalElements, totalPages, isLoading, isError } = useProducts(apiParams)

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
            {filters.searchQuery ? `Search: "${filters.searchQuery}"` : 'All Products'}
          </Typography>
          {!isLoading && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {totalElements} {totalElements === 1 ? 'product' : 'products'} found
            </Typography>
          )}
        </Box>

        <SortDropdown
          sortBy={filters.sortBy}
          sortDirection={filters.sortDirection}
          onSortChange={setSort}
        />
      </Box>

      {isError ? (
        <Box sx={{ textAlign: 'center', py: 8 }}>
          <Typography variant="h6" color="error" gutterBottom>
            Failed to load products
          </Typography>
          <Typography color="text.secondary">
            Please try again later
          </Typography>
        </Box>
      ) : (
        <>
          <ProductGrid
            products={products}
            isLoading={isLoading}
            onAddToCart={onAddToCart}
            currency={currency}
            emptyMessage={
              filters.searchQuery
                ? `No products found for "${filters.searchQuery}"`
                : 'No products found'
            }
          />

          {!isLoading && totalPages > 1 && (
            <Pagination
              page={filters.page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={filters.size}
              onPageChange={setPage}
              onPageSizeChange={setSize}
            />
          )}
        </>
      )}
    </Container>
  )
}
