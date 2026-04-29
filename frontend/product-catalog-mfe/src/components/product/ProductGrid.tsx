import { Grid, Box, Typography } from '@mui/material'
import { Inbox as EmptyIcon } from '@mui/icons-material'
import { ProductCard } from './ProductCard'
import { ProductCardSkeleton } from './ProductCardSkeleton'
import type { Product } from '../../types'

interface ProductGridProps {
  products: Product[]
  isLoading?: boolean
  skeletonCount?: number
  emptyMessage?: string
  onAddToCart?: (productId: string, quantity: number) => void
  currency?: string
}

export function ProductGrid({
  products,
  isLoading = false,
  skeletonCount = 12,
  emptyMessage = 'No products found',
  onAddToCart,
  currency,
}: ProductGridProps) {
  if (isLoading) {
    return (
      <Grid container spacing={3} data-testid="product-grid">
        {Array.from({ length: skeletonCount }).map((_, index) => (
          <Grid key={index} size={{ xs: 6, sm: 6, md: 4, lg: 3 }}>
            <ProductCardSkeleton />
          </Grid>
        ))}
      </Grid>
    )
  }

  if (products.length === 0) {
    return (
      <Box
        data-testid="product-grid-empty"
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          py: 8,
          textAlign: 'center',
        }}
      >
        <EmptyIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
        <Typography variant="h6" color="text.secondary">
          {emptyMessage}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Try adjusting your filters or search terms
        </Typography>
      </Box>
    )
  }

  return (
    <Grid container spacing={3} data-testid="product-grid">
      {products.map((product) => (
        <Grid key={product.id} size={{ xs: 6, sm: 6, md: 4, lg: 3 }}>
          <ProductCard
            product={product}
            onAddToCart={onAddToCart}
            currency={currency}
          />
        </Grid>
      ))}
    </Grid>
  )
}

export default ProductGrid
