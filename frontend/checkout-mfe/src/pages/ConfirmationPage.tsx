import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Container from '@mui/material/Container'
import Alert from '@mui/material/Alert'
import { getOrder } from '../api/orderService'
import OrderConfirmation from '../components/OrderConfirmation'
import type { Order } from '../api/types'

export default function ConfirmationPage() {
  const { orderId } = useParams<{ orderId: string }>()
  const [order, setOrder] = useState<Order | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!orderId) return
    setLoading(true)
    getOrder(orderId)
      .then(setOrder)
      .catch(() => setError('Could not load order. Please check your email for confirmation.'))
      .finally(() => setLoading(false))
  }, [orderId])

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 10 }}>
        <CircularProgress role="progressbar" />
      </Box>
    )
  }

  if (error || !order) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">{error ?? 'Could not load order.'}</Alert>
      </Container>
    )
  }

  return <OrderConfirmation order={order} />
}
