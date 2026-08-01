import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import { confirmSavedMethodPayment } from '../api/paymentMethodsService'

export interface SavedMethodConfirmButtonProps {
  paymentIntentId: string
  /** The saved Stripe payment_method id (`pm_...`) to charge — never raw card data. */
  paymentMethodId: string
  onConfirmed: (paymentIntentId: string) => void
}

const GENERIC_ERROR_MESSAGE = 'Payment could not be processed. Please try again.'
const NOT_COMPLETED_ERROR_MESSAGE =
  'Payment could not be completed. Please try another method.'

/**
 * Confirms the order's existing PaymentIntent using a previously-saved Stripe payment method by
 * calling the SERVER (`POST /api/payments/intents/confirm-saved`) — never Stripe.js directly.
 * The server verifies the payment method belongs to the caller before charging (403 otherwise),
 * so this component never trusts a client-controlled payment_method id to authorize a charge.
 * Rendered instead of <StripePaymentForm> when a returning shopper picks a saved card at the
 * Payment step (see SavedMethodPicker). No Stripe Elements are ever mounted for this path (PCI
 * SAQ-A) — 3DS/`requires_action` on a saved card is handled server-side/via webhook, not here.
 */
export default function SavedMethodConfirmButton({
  paymentIntentId,
  paymentMethodId,
  onConfirmed,
}: SavedMethodConfirmButtonProps) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setSubmitting(true)
    setError(null)

    try {
      const result = await confirmSavedMethodPayment(paymentIntentId, paymentMethodId)
      if (result.status === 'COMPLETED') {
        onConfirmed(result.paymentIntentId)
      } else {
        setError(NOT_COMPLETED_ERROR_MESSAGE)
      }
    } catch {
      setError(GENERIC_ERROR_MESSAGE)
    } finally {
      setSubmitting(false)
    }
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
