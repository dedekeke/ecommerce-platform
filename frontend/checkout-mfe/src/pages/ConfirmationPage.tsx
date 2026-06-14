import { useReducer, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Container from '@mui/material/Container'
import Alert from '@mui/material/Alert'
import { getOrder } from '../api/orderService'
import OrderConfirmation from '../components/OrderConfirmation'
import type { Order } from '../api/types'

interface PageState {
  order: Order | null
  loading: boolean
  error: string | null
}

type PageAction =
  | { type: 'SUCCESS'; order: Order }
  | { type: 'ERROR'; message: string }

function reducer(_state: PageState, action: PageAction): PageState {
  switch (action.type) {
    case 'SUCCESS':
      return { order: action.order, loading: false, error: null }
    case 'ERROR':
      return { order: null, loading: false, error: action.message }
  }
}

export default function ConfirmationPage() {
  const { orderId } = useParams<{ orderId: string }>()
  const [{ order, loading, error }, dispatch] = useReducer(reducer, {
    order: null,
    loading: true,
    error: null,
  })

  useEffect(() => {
    if (!orderId) return
    let cancelled = false
    getOrder(orderId)
      .then((o) => { if (!cancelled) dispatch({ type: 'SUCCESS', order: o }) })
      .catch(() => {
        if (!cancelled)
          dispatch({ type: 'ERROR', message: 'Could not load order. Please check your email for confirmation.' })
      })
    return () => { cancelled = true }
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
