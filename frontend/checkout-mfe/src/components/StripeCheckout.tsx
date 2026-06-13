import { useEffect, useMemo, useState } from 'react'
import { Elements } from '@stripe/react-stripe-js'
import { loadStripe, type Stripe } from '@stripe/stripe-js'
import Box from '@mui/material/Box'
import Alert from '@mui/material/Alert'
import Skeleton from '@mui/material/Skeleton'
import { createPaymentIntent } from '../api/paymentService'
import { STRIPE_PUBLISHABLE_KEY } from '../config/payments'
import StripePaymentForm from './StripePaymentForm'

export interface StripeCheckoutProps {
  orderId: string
  userId: string
  amount: number
  currency: string
  onConfirmed: (paymentIntentId: string) => void
}

// loadStripe is memoised at module scope so the SDK is fetched once per app load.
const stripePromise: Promise<Stripe | null> = loadStripe(STRIPE_PUBLISHABLE_KEY)

/**
 * Real Stripe checkout step. Creates a PaymentIntent on the backend to obtain a client_secret,
 * then renders Stripe Elements so the user can confirm the payment with Stripe.js. Enabled only
 * when VITE_PAYMENTS_PROVIDER=stripe; otherwise the legacy mock card form is used.
 */
export default function StripeCheckout({
  orderId,
  userId,
  amount,
  currency,
  onConfirmed,
}: StripeCheckoutProps) {
  const [clientSecret, setClientSecret] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setError(null)
    createPaymentIntent({ orderId, userId, amount, currency })
      .then((res) => {
        if (active) setClientSecret(res.clientSecret)
      })
      .catch(() => {
        if (active) setError('Unable to start payment. Please try again.')
      })
    return () => {
      active = false
    }
  }, [orderId, userId, amount, currency])

  const options = useMemo(
    () => (clientSecret ? { clientSecret } : undefined),
    [clientSecret]
  )

  if (error) {
    return (
      <Alert severity="error" role="alert">
        {error}
      </Alert>
    )
  }

  if (!clientSecret || !options) {
    return (
      <Box>
        <Skeleton variant="rounded" height={48} sx={{ mb: 2 }} aria-label="Loading payment form" />
        <Skeleton variant="rounded" height={48} />
      </Box>
    )
  }

  return (
    <Elements stripe={stripePromise} options={options}>
      <StripePaymentForm onConfirmed={onConfirmed} />
    </Elements>
  )
}
