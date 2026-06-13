import { useState } from 'react'
import { PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'

export interface StripePaymentFormProps {
  /** Called with the confirmed PaymentIntent id once Stripe confirmation succeeds. */
  onConfirmed: (paymentIntentId: string) => void
  /** Optional return URL for redirect-based payment methods (e.g. iDEAL, 3DS). */
  returnUrl?: string
}

/**
 * Inner Stripe confirmation form. Must be rendered inside an <Elements> provider that was
 * initialised with the PaymentIntent client_secret. Confirms the payment with Stripe.js and
 * surfaces the resulting PaymentIntent id (or a user-facing error) to the caller.
 */
export default function StripePaymentForm({ onConfirmed, returnUrl }: StripePaymentFormProps) {
  const stripe = useStripe()
  const elements = useElements()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    if (!stripe || !elements) return
    setSubmitting(true)
    setError(null)

    const { error: confirmError, paymentIntent } = await stripe.confirmPayment({
      elements,
      confirmParams: returnUrl ? { return_url: returnUrl } : {},
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

      <PaymentElement />

      <Button
        variant="contained"
        onClick={handleConfirm}
        disabled={!stripe || !elements || submitting}
        sx={{ mt: 3 }}
        fullWidth
      >
        {submitting ? <CircularProgress size={24} aria-label="Processing payment" /> : 'Pay now'}
      </Button>
    </Box>
  )
}
