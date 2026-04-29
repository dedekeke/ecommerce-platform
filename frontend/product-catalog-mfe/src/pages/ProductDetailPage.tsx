import { useState } from 'react'
import { useParams, Link as RouterLink } from 'react-router-dom'
import {
  Box,
  Typography,
  Breadcrumbs,
  Link,
  Button,
  Chip,
  Skeleton,
  IconButton,
  Paper,
  Divider,
  Grid,
} from '@mui/material'
import {
  Home as HomeIcon,
  ShoppingCart as CartIcon,
  Add as AddIcon,
  Remove as RemoveIcon,
  ArrowBack as BackIcon,
} from '@mui/icons-material'
import { useProduct } from '../hooks'

interface ProductDetailPageProps {
  onAddToCart?: (productId: string, quantity: number) => void
  currency?: string
  isAuthenticated?: boolean
}

function formatPrice(price: number, currency: string): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
  }).format(price)
}

export default function ProductDetailPage({
  onAddToCart,
  currency = 'USD',
}: ProductDetailPageProps) {
  const { productId } = useParams<{ productId: string }>()
  const { product, isLoading, isError } = useProduct(productId)
  const [quantity, setQuantity] = useState(1)
  const [selectedImageIndex, setSelectedImageIndex] = useState(0)

  const handleQuantityChange = (delta: number) => {
    setQuantity((prev) => Math.max(1, Math.min(prev + delta, product?.stockQuantity || 10)))
  }

  const handleAddToCart = () => {
    if (product && onAddToCart) {
      onAddToCart(product.id, quantity)
    }
  }

  if (isLoading) {
    return (
      <Box>
        <Skeleton variant="text" width={300} height={24} sx={{ mb: 3 }} />
        <Grid container spacing={4}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Skeleton variant="rectangular" height={400} sx={{ borderRadius: 2 }} />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Skeleton variant="text" width="80%" height={48} />
            <Skeleton variant="text" width="40%" height={32} sx={{ my: 2 }} />
            <Skeleton variant="text" width="100%" height={100} />
          </Grid>
        </Grid>
      </Box>
    )
  }

  if (isError || !product) {
    return (
      <Box sx={{ textAlign: 'center', py: 8 }}>
        <Typography variant="h5" gutterBottom>
          Product not found
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 3 }}>
          The product you're looking for doesn't exist or has been removed.
        </Typography>
        <Button
          component={RouterLink}
          to="/products"
          variant="contained"
          startIcon={<BackIcon />}
        >
          Back to Products
        </Button>
      </Box>
    )
  }

  const displayCurrency = currency || product.currency
  const images = product.images?.length > 0 ? product.images : ['/placeholder-product.png']

  return (
    <Box>
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
        <Link
          component={RouterLink}
          to="/products"
          color="inherit"
          underline="hover"
        >
          Products
        </Link>
        {product.category && (
          <Link
            component={RouterLink}
            to={`/products?category=${product.category.id}`}
            color="inherit"
            underline="hover"
          >
            {product.category.name}
          </Link>
        )}
        <Typography color="text.primary" noWrap sx={{ maxWidth: 200 }}>
          {product.name}
        </Typography>
      </Breadcrumbs>

      <Grid container spacing={4}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper
            elevation={0}
            sx={{
              p: 2,
              borderRadius: 2,
              border: '1px solid',
              borderColor: 'grey.100',
              backgroundColor: 'grey.50',
            }}
          >
            <Box
              component="img"
              src={images[selectedImageIndex]}
              alt={product.name}
              sx={{
                width: '100%',
                height: 400,
                objectFit: 'contain',
                borderRadius: 1,
              }}
            />
          </Paper>

          {images.length > 1 && (
            <Box sx={{ display: 'flex', gap: 1, mt: 2, overflowX: 'auto', pb: 1 }}>
              {images.map((image, index) => (
                <Box
                  key={index}
                  component="img"
                  src={image}
                  alt={`${product.name} - ${index + 1}`}
                  onClick={() => setSelectedImageIndex(index)}
                  sx={{
                    width: 80,
                    height: 80,
                    objectFit: 'cover',
                    borderRadius: 1,
                    cursor: 'pointer',
                    border: '2px solid',
                    borderColor: selectedImageIndex === index ? 'primary.main' : 'grey.200',
                    opacity: selectedImageIndex === index ? 1 : 0.7,
                    transition: 'all 0.2s',
                    '&:hover': {
                      opacity: 1,
                      borderColor: 'primary.light',
                    },
                  }}
                />
              ))}
            </Box>
          )}
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Box>
            <Chip
              label={product.category?.name}
              size="small"
              sx={{ mb: 1 }}
            />

            <Typography variant="h4" fontWeight={700} gutterBottom>
              {product.name}
            </Typography>

            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              SKU: {product.sku}
            </Typography>

            <Typography
              variant="h4"
              fontWeight={700}
              color="primary"
              sx={{ mb: 3, fontFamily: '"JetBrains Mono", monospace' }}
            >
              {formatPrice(product.price, displayCurrency)}
            </Typography>

            <Chip
              label={product.inStock ? 'In Stock' : 'Out of Stock'}
              color={product.inStock ? 'success' : 'error'}
              size="small"
              sx={{ mb: 3 }}
            />

            <Divider sx={{ my: 3 }} />

            <Typography variant="body1" sx={{ mb: 3, lineHeight: 1.8 }}>
              {product.description}
            </Typography>

            <Divider sx={{ my: 3 }} />

            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
              <Typography variant="body1" fontWeight={500}>
                Quantity:
              </Typography>
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  border: '1px solid',
                  borderColor: 'grey.300',
                  borderRadius: 1,
                }}
              >
                <IconButton
                  size="small"
                  onClick={() => handleQuantityChange(-1)}
                  disabled={quantity <= 1}
                >
                  <RemoveIcon />
                </IconButton>
                <Typography sx={{ px: 2, minWidth: 40, textAlign: 'center' }}>
                  {quantity}
                </Typography>
                <IconButton
                  size="small"
                  onClick={() => handleQuantityChange(1)}
                  disabled={quantity >= (product.stockQuantity || 10)}
                >
                  <AddIcon />
                </IconButton>
              </Box>
              {product.stockQuantity && (
                <Typography variant="body2" color="text.secondary">
                  {product.stockQuantity} available
                </Typography>
              )}
            </Box>

            <Button
              variant="contained"
              size="large"
              fullWidth
              startIcon={<CartIcon />}
              onClick={handleAddToCart}
              disabled={!product.inStock}
              sx={{
                py: 1.5,
                borderRadius: 2,
                fontWeight: 600,
                fontSize: '1rem',
              }}
            >
              Add to Cart
            </Button>
          </Box>
        </Grid>
      </Grid>
    </Box>
  )
}
