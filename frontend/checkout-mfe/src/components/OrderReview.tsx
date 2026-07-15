import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Divider from '@mui/material/Divider'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined'
import CreditCardIcon from '@mui/icons-material/CreditCard'
import type { ShippingAddress } from '../api/types'
import { useCartStore, selectCartItems, selectCartTotal } from '../stores/cartStore'

const TAX_RATE = 0.1
const SHIPPING_FLAT = 5
const FREE_SHIPPING_THRESHOLD = 50

interface OrderReviewProps {
  address: ShippingAddress
}

// Order-first checkout (PR#122): payment happens after this review step, once the order is
// created and a PaymentIntent client_secret is available — so there is no payment method to
// show yet here.
export default function OrderReview({ address }: OrderReviewProps) {
  const items = useCartStore(selectCartItems)
  const subtotal = useCartStore(selectCartTotal)
  const tax = subtotal * TAX_RATE
  const isFreeShipping = subtotal >= FREE_SHIPPING_THRESHOLD
  const shipping = isFreeShipping ? 0 : SHIPPING_FLAT
  const total = subtotal + tax + shipping

  return (
    <Box>
      <Typography variant="h6" fontWeight={600} sx={{ mb: 3 }}>
        Review your order
      </Typography>

      <Box
        sx={{
          bgcolor: 'background.paper',
          borderRadius: 2,
          p: 2.5,
          mb: 3,
          boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
        }}
      >
        <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>
          Shipping address
        </Typography>
        <Typography variant="body2">{address.fullName}</Typography>
        <Typography variant="body2">{address.line1}</Typography>
        {address.line2 && <Typography variant="body2">{address.line2}</Typography>}
        <Typography variant="body2">
          {address.city}, {address.state} {address.postalCode}
        </Typography>
        <Typography variant="body2">{address.country}</Typography>
      </Box>

      <Box
        sx={{
          bgcolor: 'background.paper',
          borderRadius: 2,
          p: 2.5,
          mb: 3,
          boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <CreditCardIcon fontSize="small" sx={{ color: 'text.secondary' }} />
        <Typography variant="body2" color="text.secondary">
          You&apos;ll enter payment details securely on the next step.
        </Typography>
      </Box>

      <Box
        sx={{
          bgcolor: 'background.paper',
          borderRadius: 2,
          p: 2.5,
          boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
        }}
      >
        <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 2 }}>
          Order items
        </Typography>

        {items.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No items in cart
          </Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>
                  <Typography variant="caption" fontWeight={600}>
                    Product
                  </Typography>
                </TableCell>
                <TableCell align="center">
                  <Typography variant="caption" fontWeight={600}>
                    Qty
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <Typography variant="caption" fontWeight={600}>
                    Price
                  </Typography>
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.productId}>
                  <TableCell>
                    <Typography variant="body2">{item.name}</Typography>
                  </TableCell>
                  <TableCell align="center">
                    <Typography variant="body2">{item.quantity}</Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="body2">
                      {`$${(item.price * item.quantity).toFixed(2)}`}
                    </Typography>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        <Divider sx={{ my: 2 }} />

        <Stack spacing={1}>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">
              Subtotal
            </Typography>
            <Typography variant="body2">{`$${subtotal.toFixed(2)}`}</Typography>
          </Stack>

          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">
              Tax (10%)
            </Typography>
            <Typography variant="body2">{`$${tax.toFixed(2)}`}</Typography>
          </Stack>

          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Stack direction="row" alignItems="center" gap={0.5}>
              <LocalShippingOutlinedIcon fontSize="small" sx={{ color: 'text.secondary' }} />
              <Typography variant="body2" color="text.secondary">
                Shipping
              </Typography>
            </Stack>
            {isFreeShipping ? (
              <Chip
                label="Free"
                size="small"
                color="success"
                variant="outlined"
                sx={{ fontWeight: 600, height: 22 }}
              />
            ) : (
              <Typography variant="body2">{`$${shipping.toFixed(2)}`}</Typography>
            )}
          </Stack>

          <Divider />

          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body1" fontWeight={700}>
              Total
            </Typography>
            <Typography variant="body1" fontWeight={700} color="primary.main">
              {`$${total.toFixed(2)}`}
            </Typography>
          </Stack>
        </Stack>
      </Box>
    </Box>
  )
}
