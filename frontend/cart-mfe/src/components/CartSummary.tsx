import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Divider from '@mui/material/Divider'
import Stack from '@mui/material/Stack'
import Chip from '@mui/material/Chip'
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined'

const TAX_RATE = 0.1
const SHIPPING_FLAT = 5
const FREE_SHIPPING_THRESHOLD = 50

interface CartSummaryProps {
  subtotal: number
  onCheckout: () => void
}

export default function CartSummary({ subtotal, onCheckout }: CartSummaryProps) {
  const tax = subtotal * TAX_RATE
  const isFreeShipping = subtotal >= FREE_SHIPPING_THRESHOLD
  const shipping = isFreeShipping ? 0 : SHIPPING_FLAT
  const total = subtotal + tax + shipping
  const amountToFreeShipping = FREE_SHIPPING_THRESHOLD - subtotal

  return (
    <Box
      sx={{
        bgcolor: 'background.paper',
        borderRadius: 3,
        p: 3,
        boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
      }}
    >
      <Typography variant="h6" fontWeight={600} gutterBottom>
        Order Summary
      </Typography>

      <Divider sx={{ mb: 2 }} />

      <Stack spacing={1.5}>
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">
            Subtotal
          </Typography>
          <Typography variant="body2" fontWeight={500}>
            ${subtotal.toFixed(2)}
          </Typography>
        </Stack>

        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">
            Est. Tax (10%)
          </Typography>
          <Typography variant="body2" fontWeight={500}>
            ${tax.toFixed(2)}
          </Typography>
        </Stack>

        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="body2" color="text.secondary">
            Shipping
          </Typography>
          {isFreeShipping ? (
            <Chip
              label="Free"
              size="small"
              color="success"
              variant="outlined"
              sx={{ fontWeight: 600, height: 22 }}
            />
          ) : (
            <Typography variant="body2" fontWeight={500}>
              ${shipping.toFixed(2)}
            </Typography>
          )}
        </Stack>
      </Stack>

      {!isFreeShipping && subtotal > 0 && (
        <Box
          sx={{
            mt: 2,
            p: 1.5,
            bgcolor: 'secondary.main' + '12',
            borderRadius: 2,
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
        >
          <LocalShippingOutlinedIcon fontSize="small" sx={{ color: 'secondary.main' }} />
          <Typography variant="caption" color="secondary.dark">
            Add ${amountToFreeShipping.toFixed(2)} more for free shipping
          </Typography>
        </Box>
      )}

      <Divider sx={{ my: 2 }} />

      <Stack direction="row" justifyContent="space-between" sx={{ mb: 3 }}>
        <Typography variant="body1" fontWeight={700}>
          Total
        </Typography>
        <Typography variant="body1" fontWeight={700} color="primary.main">
          ${total.toFixed(2)}
        </Typography>
      </Stack>

      <Button
        variant="contained"
        fullWidth
        size="large"
        onClick={onCheckout}
        disabled={subtotal === 0}
        sx={{
          py: 1.5,
          fontWeight: 600,
          fontSize: '1rem',
          transition: 'all 0.2s ease',
          '&:not(:disabled):hover': {
            transform: 'translateY(-1px)',
            boxShadow: '0 4px 12px rgba(26,26,46,0.25)',
          },
        }}
      >
        Proceed to checkout
      </Button>
    </Box>
  )
}
