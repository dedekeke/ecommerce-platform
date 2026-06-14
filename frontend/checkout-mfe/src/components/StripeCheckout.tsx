import { useEffect, useMemo, useState } from 'react'
import { Elements } from '@stripe/react-stripe-js'
import type { Stripe } from '@stripe/stripe-js'
import Box from '@mui/material/Box'
import Alert from '@mui/material/Alert'
import Skeleton from '@mui/material/Skeleton'
import { createPaymentIntent } from '../api/paymentService'
import { STRIPE_PUBLISHABLE_KEY } from '../config/payments'
import StripePaymentForm from './StripePaymentForm'

// The ~120KB Stripe JS SDK is loaded via a dynamic import so it lands in its own chunk and is only
// fetched when the Stripe provider is actually active. Memoised at module scope so the SDK is
// fetched at most once. Returns null (without importing) when there is no publishable key
// (provider=mock) — loadStripe would otherwise throw on an empty key.
let stripePromise: Promise<Stripe | null> | null = null
const getStripe = (): Promise<Stripe | null> | null => {
  if (!STRIPE_PUBLISHABLE_KEY) return null
  if (!stripePromise) {
    stripePromise = import('@stripe/stripe-js').then(({ loadStripe }) =>
      loadStripe(STRIPE_PUBLISHABLE_KEY)
    )
  }
  return stripePromise
}

export interface StripeCheckoutProps {
  orderId: string
  userId: string
  amount: number
  currency: string
  onConfirmed: (paymentIntentId: string) => void
}

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
  const stripePromise = useMemo(() => getStripe(), [])

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

  if (!stripePromise) {
    return (
      <Alert severity="error" role="alert">
        Payment is not configured. Please contact support.
      </Alert>
    )
  }

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
