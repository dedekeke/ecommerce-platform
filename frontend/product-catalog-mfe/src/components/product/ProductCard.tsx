import { Link } from 'react-router-dom'
import {
  Card,
  CardContent,
  CardActions,
  Typography,
  Button,
  Chip,
  Box,
  styled,
} from '@mui/material'
import {
  ShoppingCart as CartIcon,
  ImageNotSupported as NoImageIcon,
} from '@mui/icons-material'
import type { Product } from '../../types'

interface ProductCardProps {
  product: Product
  onAddToCart?: (productId: string, quantity: number) => void
  currency?: string
}

const StyledCard = styled(Card)(({ theme }) => ({
  height: '100%',
  display: 'flex',
  flexDirection: 'column',
  position: 'relative',
  borderRadius: 16,
  border: `1px solid ${theme.palette.grey[100]}`,
  transition: 'all 0.2s ease-out',
  '&:hover': {
    transform: 'translateY(-4px)',
    boxShadow: '0 20px 40px -10px rgba(0, 0, 0, 0.15)',
  },
}))

const ImageContainer = styled(Box)({
  position: 'relative',
  paddingTop: '100%',
  overflow: 'hidden',
  backgroundColor: '#f5f5f5',
})

const StyledCardMedia = styled('img')({
  position: 'absolute',
  top: 0,
  left: 0,
  width: '100%',
  height: '100%',
  objectFit: 'cover',
})

const ImagePlaceholder = styled(Box)(({ theme }) => ({
  position: 'absolute',
  top: 0,
  left: 0,
  width: '100%',
  height: '100%',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  backgroundColor: theme.palette.grey[100],
  color: theme.palette.grey[400],
}))

const StockChip = styled(Chip)<{ instock: string }>(({ theme, instock }) => ({
  position: 'absolute',
  top: 12,
  right: 12,
  fontWeight: 500,
  fontSize: '0.75rem',
  backgroundColor: instock === 'true' ? theme.palette.success.main : theme.palette.error.main,
  color: '#fff',
}))

const ProductName = styled(Typography)({
  fontWeight: 600,
  display: '-webkit-box',
  WebkitLineClamp: 2,
  WebkitBoxOrient: 'vertical',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  minHeight: '3rem',
})

const PriceTypography = styled(Typography)(({ theme }) => ({
  fontWeight: 700,
  fontSize: '1.25rem',
  color: theme.palette.primary.main,
  fontFamily: '"JetBrains Mono", monospace',
}))

const CardLink = styled(Link)({
  textDecoration: 'none',
  color: 'inherit',
  display: 'block',
  flexGrow: 1,
})

function formatPrice(price: number, currency: string): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currency,
  }).format(price)
}

export function ProductCard({ product, onAddToCart, currency = 'USD' }: ProductCardProps) {
  const { id, name, price, images, category, inStock } = product
  const displayCurrency = currency || product.currency

  const handleAddToCart = (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (onAddToCart && inStock) {
      onAddToCart(id, 1)
    }
  }

  return (
    <StyledCard data-testid="product-card">
      <CardLink to={`/products/${id}`}>
        <ImageContainer>
          {images && images.length > 0 ? (
            <StyledCardMedia
              src={images[0]}
              alt={name}
            />
          ) : (
            <ImagePlaceholder data-testid="product-image-placeholder">
              <NoImageIcon sx={{ fontSize: 48 }} />
            </ImagePlaceholder>
          )}
          <StockChip
            label={inStock ? 'In Stock' : 'Out of Stock'}
            size="small"
            instock={inStock.toString()}
          />
        </ImageContainer>

        <CardContent sx={{ flexGrow: 1, pb: 1 }}>
          {category && (
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ mb: 0.5, display: 'block' }}
            >
              {category.name}
            </Typography>
          )}
          <ProductName variant="body1" gutterBottom>
            {name}
          </ProductName>
          <PriceTypography>
            {formatPrice(price, displayCurrency)}
          </PriceTypography>
        </CardContent>
      </CardLink>

      <CardActions sx={{ px: 2, pb: 2, pt: 0 }}>
        <Button
          variant="contained"
          fullWidth
          startIcon={<CartIcon />}
          onClick={handleAddToCart}
          disabled={!inStock}
          sx={{
            borderRadius: 2,
            py: 1,
            textTransform: 'none',
            fontWeight: 600,
          }}
        >
          Add to Cart
        </Button>
      </CardActions>
    </StyledCard>
  )
}

export default ProductCard
