import { useCallback, useMemo, useState } from 'react'
import { Elements } from '@stripe/react-stripe-js'
import type { Stripe } from '@stripe/stripe-js'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import { STRIPE_PUBLISHABLE_KEY } from '../config/payments'
import StripePaymentForm from './StripePaymentForm'
import SavedMethodPicker, { type SavedMethodSelection } from './SavedMethodPicker'
import SavedMethodConfirmButton from './SavedMethodConfirmButton'

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
  /**
   * Authenticated user id. When present, a saved-card picker (SavedMethodPicker) is shown above
   * the new-card form so a returning shopper can reuse a stored Stripe payment method instead of
   * retyping a card. Omit/null for guest checkout — guests never have saved methods and must
   * never be queried for them.
   */
  userId?: string | null
}

/**
 * Real Stripe checkout step. For an authenticated user, offers a choice between a previously
 * saved payment method (confirmed directly by its payment_method id, no Elements mounted) and the
 * new-card flow (Stripe Elements against the order's clientSecret). Guests only ever see the
 * new-card flow. This is the only payment step in checkout — raw card data is never collected by
 * this app (PCI SAQ-A).
 */
export default function StripeCheckout({ clientSecret, onConfirmed, userId }: StripeCheckoutProps) {
  const stripeInstance = useMemo(() => getStripe(), [])
  const options = useMemo(() => ({ clientSecret }), [clientSecret])
  const [selection, setSelection] = useState<SavedMethodSelection>({ type: 'new' })

  const handleSelectionChange = useCallback((next: SavedMethodSelection) => setSelection(next), [])

  if (!stripeInstance) {
    return (
      <Alert severity="error" role="alert">
        Payment is not configured. Please contact support.
      </Alert>
    )
  }

  return (
    <Box>
      {userId && <SavedMethodPicker userId={userId} onSelectionChange={handleSelectionChange} />}

      {selection.type === 'saved' ? (
        <SavedMethodConfirmButton
          stripePromise={stripeInstance}
          clientSecret={clientSecret}
          providerId={selection.method.providerId}
          onConfirmed={onConfirmed}
        />
      ) : (
        <Elements stripe={stripeInstance} options={options}>
          <StripePaymentForm onConfirmed={onConfirmed} />
        </Elements>
      )}
    </Box>
  )
}
