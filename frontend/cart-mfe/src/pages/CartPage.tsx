import Box from '@mui/material/Box'
import Container from '@mui/material/Container'
import Grid from '@mui/material/Grid'
import Typography from '@mui/material/Typography'
import { useNavigate } from 'react-router-dom'
import CartList from '../components/CartList'
import CartSummary from '../components/CartSummary'
import { useCart } from '../hooks/useCart'
import { useCartSync } from '../hooks/useCartSync'
import { useCartServerSyncEnabled } from '../lib/cartSync'

export default function CartPage() {
  const { items, total, removeItem, updateQuantity } = useCart()
  // Hydrate from cart-service so Review/checkout always match the order saga's view.
  // useCartServerSyncEnabled (not a render-time snapshot): on a deep-linked hard reload the
  // shell installs the auth accessor AFTER this MFE's first render — see lib/cartSync.
  const serverSyncEnabled = useCartServerSyncEnabled()
  const { syncing } = useCartSync(serverSyncEnabled)
  const navigate = useNavigate()

  const handleCheckout = () => {
    navigate('/checkout')
  }

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '100vh' }}>
      <Container
        maxWidth="lg"
        sx={{
          px: { xs: 3, md: 4 },
          py: { xs: 3, md: 4 },
        }}
      >
        <Typography
          variant="h4"
          fontWeight={700}
          gutterBottom
          sx={{ mb: { xs: 3, md: 4 } }}
        >
          Shopping Cart
          {items.length > 0 && (
            <Typography
              component="span"
              variant="body1"
              color="text.secondary"
              sx={{ ml: 1.5, fontWeight: 400 }}
            >
              ({items.length} {items.length === 1 ? 'item' : 'items'})
            </Typography>
          )}
        </Typography>

        <Grid container spacing={{ xs: 2, md: 4 }}>
          <Grid size={{ xs: 12, md: 8 }}>
            <CartList
              items={items}
              loading={syncing && items.length === 0}
              onUpdateQty={updateQuantity}
              onRemove={removeItem}
            />
          </Grid>

          {items.length > 0 && (
            <Grid size={{ xs: 12, md: 4 }}>
              <Box sx={{ position: { md: 'sticky' }, top: { md: 24 } }}>
                <CartSummary subtotal={total} onCheckout={handleCheckout} />
              </Box>
            </Grid>
          )}
        </Grid>
      </Container>
    </Box>
  )
}
