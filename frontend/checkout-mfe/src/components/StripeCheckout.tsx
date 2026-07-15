import { useMemo } from 'react'
import { Elements } from '@stripe/react-stripe-js'
import type { Stripe } from '@stripe/stripe-js'
import Alert from '@mui/material/Alert'
import { STRIPE_PUBLISHABLE_KEY } from '../config/payments'
import StripePaymentForm from './StripePaymentForm'

// The ~120KB Stripe JS SDK is loaded via a dynamic import so it lands in its own chunk and is only
// fetched when the Stripe provider is actually active. Memoised at module scope so the SDK is
// fetched at most once. Returns null (without importing) when there is no publishable key —
// loadStripe would otherwise throw on an empty key.
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
  /**
   * The PaymentIntent client_secret returned by `POST /api/orders` (order-first checkout, see
   * orderService.createOrder / PR#122). This app never creates its own PaymentIntent — the
   * order-creation saga already did — and never collects raw card data (PCI SAQ-A).
   */
  clientSecret: string
  onConfirmed: (paymentIntentId: string) => void
}

/**
 * Real Stripe checkout step. Renders Stripe Elements against a clientSecret obtained from order
 * creation so the user can confirm the payment with Stripe.js. This is the only payment step in
 * checkout — raw card data is never collected by this app (PCI SAQ-A).
 */
export default function StripeCheckout({ clientSecret, onConfirmed }: StripeCheckoutProps) {
  const stripeInstance = useMemo(() => getStripe(), [])
  const options = useMemo(() => ({ clientSecret }), [clientSecret])

  if (!stripeInstance) {
    return (
      <Alert severity="error" role="alert">
        Payment is not configured. Please contact support.
      </Alert>
    )
  }

  return (
    <Elements stripe={stripeInstance} options={options}>
      <StripePaymentForm onConfirmed={onConfirmed} />
    </Elements>
  )
}
