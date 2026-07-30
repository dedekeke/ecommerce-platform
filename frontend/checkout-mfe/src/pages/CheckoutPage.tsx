import { lazy, Suspense, useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { isAxiosError } from 'axios'
import Box from '@mui/material/Box'
import Container from '@mui/material/Container'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import Skeleton from '@mui/material/Skeleton'
import { useCheckout } from '../hooks/useCheckout'
import { useAuthUserId } from '../hooks/useAuthUserId'
import { useCheckoutStore } from '../stores/checkoutStore'
import { createOrder, createGuestOrder } from '../api/orderService'
import { toAddressDto } from '../utils/toAddressDto'
import { toast } from '../lib/toast'
import AddressForm from '../components/AddressForm'
import OrderReview from '../components/OrderReview'
import CheckoutStepper from '../components/CheckoutStepper'
import GuestAuthGate from '../components/GuestAuthGate'
import type { ShippingAddress } from '../api/types'

// Code-split the Stripe step: StripeCheckout statically pulls @stripe/stripe-js +
// @stripe/react-stripe-js (~120KB). Lazy-loading keeps that SDK out of the main checkout bundle
// and only fetches it when the user reaches the payment step.
const StripeCheckout = lazy(() => import('../components/StripeCheckout'))

// Order-first checkout (PR#122): the Review step submits POST /api/orders, which creates the
// order AND the Stripe PaymentIntent server-side. Only once that response is in hand do we know
// the client_secret needed to mount Stripe Elements — so Payment must come after Review.
const STEPS = ['Shipping', 'Review', 'Payment']

interface CheckoutResult {
  orderId: string
  clientSecret: string
}

export default function CheckoutPage() {
  const navigate = useNavigate()
  const {
    step,
    address,
    idempotencyKey,
    isFirstStep,
    isLastStep,
    canProceed,
    goNext,
    goBack,
    setAddress,
    reset,
  } = useCheckout()

  const userId = useAuthUserId()
  const guestEmail = useCheckoutStore((s) => s.guestEmail)
  const setGuestEmail = useCheckoutStore((s) => s.setGuestEmail)

  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [checkoutResult, setCheckoutResult] = useState<CheckoutResult | null>(null)

  const handleAddressValid = useCallback((addr: ShippingAddress) => setAddress(addr), [setAddress])

  // Auth gate. An authenticated shopper (userId) checks out as themselves. An
  // unauthenticated one is no longer hard-blocked: they may continue as guest by
  // providing an email, which unlocks the same flow but submits to the guest
  // endpoint. Only when neither identity is present do we show the gate.
  if (!userId && !guestEmail) {
    return (
      <Box sx={{ bgcolor: 'background.default', minHeight: '100vh' }}>
        <Container maxWidth="md" sx={{ px: { xs: 3, md: 4 }, py: { xs: 3, md: 5 } }}>
          <Typography variant="h4" fontWeight={700} sx={{ mb: { xs: 3, md: 4 } }}>
            Checkout
          </Typography>
          <GuestAuthGate onContinueAsGuest={setGuestEmail} />
        </Container>
      </Box>
    )
  }

  // Submits the order (POST /api/orders). This is the Review step's action: the saga creates
  // the order and the PaymentIntent server-side, so the resulting clientSecret is what unlocks
  // the Payment step. idempotencyKey is stable across retries of this attempt (see
  // checkoutStore) so a 409 (in-flight) is never blindly retried and a transient 502 (retried
  // automatically by apiClient) never creates a duplicate order.
  const submitOrder = async () => {
    if (!address) return
    // Re-read the live identity at submit time: the session may have expired since render.
    const liveUserId = window.__getAuthUserId?.() ?? null
    // An authenticated session takes precedence. If there is neither a live
    // session nor a guest email, the identity vanished after the gate — block.
    if (!liveUserId && !guestEmail) {
      setSubmitError('Your session has expired. Please sign in again to place the order.')
      return
    }
    setIsSubmitting(true)
    setSubmitError(null)
    try {
      // Authenticated -> POST /api/orders (JWT sub is authoritative). Guest ->
      // POST /api/orders/guest (server derives identity from the email). Both
      // return the same clientSecret contract, so the rest of the flow is shared.
      const { response, isReplay } = liveUserId
        ? await createOrder(
            { userId: liveUserId, shippingAddress: toAddressDto(address) },
            idempotencyKey
          )
        : await createGuestOrder(
            { email: guestEmail as string, shippingAddress: toAddressDto(address) },
            idempotencyKey
          )

      if (isReplay && !response.clientSecret) {
        // Already-completed checkout for this idempotency key and no secret was re-issued —
        // nothing left to confirm here. Route to the order-status/confirmation view rather than
        // mounting Stripe Elements with nothing to confirm.
        reset()
        navigate(`confirmation/${response.orderId}`)
        return
      }

      if (!response.clientSecret) {
        setSubmitError('Order created but payment could not be started. Please contact support.')
        return
      }

      setCheckoutResult({ orderId: response.orderId, clientSecret: response.clientSecret })
      goNext()
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 409) {
        // A checkout with this idempotency key is already being processed (concurrent
        // double-submit) — must NOT retry; retrying would just re-trigger this same guard.
        setSubmitError('Your order is already being submitted. Please wait a moment before trying again.')
      } else {
        setSubmitError('Failed to place order. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleNext = () => {
    if (step === 0) {
      goNext()
      return
    }
    if (step === 1) {
      void submitOrder()
    }
    // step === 2 (Payment) has no generic "Next" action — the stepper's action bar is hidden
    // there; Stripe's own confirm button drives completion.
  }

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '100vh' }}>
      <Container
        maxWidth="md"
        sx={{ px: { xs: 3, md: 4 }, py: { xs: 3, md: 5 } }}
      >
        <Typography variant="h4" fontWeight={700} sx={{ mb: { xs: 3, md: 4 } }}>
          Checkout
        </Typography>

        {submitError && (
          <Alert severity="error" sx={{ mb: 3 }} onClose={() => setSubmitError(null)}>
            {submitError}
          </Alert>
        )}

        <Box sx={{ mb: 4 }}>
          {step === 0 && (
            <AddressForm
              onValid={handleAddressValid}
              onChange={() => {}}
              initialValues={address ?? undefined}
            />
          )}

          {step === 1 && address && <OrderReview address={address} />}

          {step === 2 && checkoutResult && (
            <Suspense
              fallback={
                <Box>
                  <Skeleton variant="rounded" height={48} sx={{ mb: 2 }} aria-label="Loading payment" />
                  <Skeleton variant="rounded" height={48} />
                </Box>
              }
            >
              <StripeCheckout
                clientSecret={checkoutResult.clientSecret}
                userId={userId}
                onConfirmed={() => {
                  toast.success('Order placed successfully!')
                  reset()
                  navigate(`confirmation/${checkoutResult.orderId}`)
                }}
              />
            </Suspense>
          )}
        </Box>

        <CheckoutStepper
          steps={STEPS}
          activeStep={step}
          onBack={goBack}
          onNext={handleNext}
          canProceed={canProceed}
          isLastStep={isLastStep}
          isFirstStep={isFirstStep}
          isSubmitting={isSubmitting}
          nextLabel={step === 1 ? 'Continue to payment' : undefined}
          hideActions={step === 2}
        />
      </Container>
    </Box>
  )
}
