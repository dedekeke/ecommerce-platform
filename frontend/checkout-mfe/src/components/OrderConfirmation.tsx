import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import Stack from '@mui/material/Stack'
import Divider from '@mui/material/Divider'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import { Link as RouterLink } from 'react-router-dom'
import type { Order } from '../api/types'

interface OrderConfirmationProps {
  order: Order
}

export default function OrderConfirmation({ order }: OrderConfirmationProps) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        py: { xs: 4, md: 8 },
        px: 2,
        textAlign: 'center',
      }}
    >
      <CheckCircleIcon
        sx={{
          fontSize: 80,
          color: 'success.main',
          mb: 3,
          animation: 'popIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)',
          '@keyframes popIn': {
            from: { transform: 'scale(0)', opacity: 0 },
            to: { transform: 'scale(1)', opacity: 1 },
          },
        }}
      />

      <Typography variant="h4" fontWeight={700} gutterBottom>
        Order confirmed!
      </Typography>

      <Typography variant="body1" color="text.secondary" sx={{ mb: 4, maxWidth: 480 }}>
        Thank you for your purchase. Your order is being processed and you will receive a
        confirmation email shortly.
      </Typography>

      <Box
        sx={{
          bgcolor: 'background.paper',
          borderRadius: 3,
          p: 3,
          boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
          width: '100%',
          maxWidth: 480,
          mb: 4,
        }}
      >
        <Stack spacing={1.5}>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">
              Order number
            </Typography>
            <Typography variant="body2" fontWeight={600}>
              {order.orderNumber}
            </Typography>
          </Stack>

          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">
              Status
            </Typography>
            <Typography variant="body2" fontWeight={600} sx={{ textTransform: 'capitalize' }}>
              {order.status.toLowerCase()}
            </Typography>
          </Stack>

          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">
              Total
            </Typography>
            <Typography variant="body2" fontWeight={600}>
              {`$${order.total.toFixed(2)}`}
            </Typography>
          </Stack>

          <Divider />

          <Typography variant="body2" color="text.secondary">
            Estimated delivery:{' '}
            <Typography component="span" variant="body2" fontWeight={600} color="text.primary">
              5–7 business days
            </Typography>
          </Typography>
        </Stack>
      </Box>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <Button
          variant="contained"
          component={RouterLink}
          to={`/orders/${order.orderId}`}
          size="large"
          sx={{
            fontWeight: 600,
            transition: 'all 0.2s ease',
            '&:hover': { transform: 'translateY(-1px)', boxShadow: '0 4px 12px rgba(26,26,46,0.25)' },
          }}
        >
          View order
        </Button>

        <Button
          variant="outlined"
          component={RouterLink}
          to="/"
          size="large"
        >
          Continue shopping
        </Button>
      </Stack>
    </Box>
  )
}
