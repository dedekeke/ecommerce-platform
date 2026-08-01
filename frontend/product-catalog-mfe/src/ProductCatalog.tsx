import { Routes, Route, Navigate } from 'react-router-dom'
import { Box } from '@mui/material'
import ProductListPage from './pages/ProductListPage'
import ProductDetailPage from './pages/ProductDetailPage'

export interface ProductCatalogProps {
  onAddToCart?: (productId: string, quantity: number) => void
  currency?: string
  isAuthenticated?: boolean
}

export default function ProductCatalog({
  onAddToCart,
  currency = 'USD',
  isAuthenticated = false,
}: ProductCatalogProps) {
  return (
    <Box sx={{ minHeight: '50vh' }}>
      <Routes>
        <Route
          index
          element={
            <ProductListPage
              onAddToCart={onAddToCart}
              currency={currency}
            />
          }
        />
        <Route
          path=":productId"
          element={
            <ProductDetailPage
              onAddToCart={onAddToCart}
              currency={currency}
              isAuthenticated={isAuthenticated}
            />
          }
        />
        <Route
          path="category/:categorySlug"
          element={
            <ProductListPage
              onAddToCart={onAddToCart}
              currency={currency}
            />
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Box>
  )
}
