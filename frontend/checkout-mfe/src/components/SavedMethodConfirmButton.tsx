import { useState } from 'react'
import type { Stripe } from '@stripe/stripe-js'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'

export interface SavedMethodConfirmButtonProps {
  /** Resolves to the lazily-loaded Stripe.js instance (see StripeCheckout's module-scoped
   * getStripe). Deliberately not wrapped in an `<Elements>` provider: a saved method is confirmed
   * by its `payment_method` id alone, so no card-input Elements are ever mounted (PCI SAQ-A). */
  stripePromise: Promise<Stripe | null>
  clientSecret: string
  /** The saved Stripe payment_method id (`pm_...`) to charge — never raw card data. */
  providerId: string
  onConfirmed: (paymentIntentId: string) => void
}

/**
 * Confirms the order's existing PaymentIntent using a previously-saved Stripe payment method,
 * without mounting Stripe Elements. Rendered instead of <StripePaymentForm> when a returning
 * shopper picks a saved card at the Payment step (see SavedMethodPicker).
 */
export default function SavedMethodConfirmButton({
  stripePromise,
  clientSecret,
  providerId,
  onConfirmed,
}: SavedMethodConfirmButtonProps) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setSubmitting(true)
    setError(null)

    const stripe = await stripePromise
    if (!stripe) {
      setError('Payment is not configured. Please contact support.')
      setSubmitting(false)
      return
    }

    const { error: confirmError, paymentIntent } = await stripe.confirmPayment({
      clientSecret,
      confirmParams: { payment_method: providerId },
      redirect: 'if_required',
    })

    if (confirmError) {
      setError(confirmError.message ?? 'Payment could not be processed. Please try again.')
      setSubmitting(false)
      return
    }

    if (paymentIntent && paymentIntent.status === 'succeeded') {
      onConfirmed(paymentIntent.id)
    } else {
      setError('Payment was not completed. Please try a different payment method.')
    }
    setSubmitting(false)
  }

  return (
    <Box>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}

      <Button variant="contained" onClick={handleConfirm} disabled={submitting} sx={{ mt: 1 }} fullWidth>
        {submitting ? <CircularProgress size={24} aria-label="Processing payment" /> : 'Pay now'}
      </Button>
    </Box>
  )
}
