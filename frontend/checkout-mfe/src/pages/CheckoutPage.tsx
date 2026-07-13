import { lazy, Suspense, useCallback, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Box from '@mui/material/Box'
import Container from '@mui/material/Container'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import Skeleton from '@mui/material/Skeleton'
import { useCheckout } from '../hooks/useCheckout'
import { useAuthUserId } from '../hooks/useAuthUserId'
import { useCartStore, selectCartItems, selectCartTotal } from '../stores/cartStore'
import { createOrder } from '../api/orderService'
import { isStripeEnabled } from '../config/payments'
import AddressForm from '../components/AddressForm'
import PaymentMethodForm from '../components/PaymentMethodForm'
import OrderReview from '../components/OrderReview'
import CheckoutStepper from '../components/CheckoutStepper'
import type { ShippingAddress } from '../api/types'

// Code-split the Stripe step: StripeCheckout statically pulls @stripe/stripe-js +
// @stripe/react-stripe-js (~120KB). Lazy-loading keeps that SDK out of the main checkout bundle
// and only fetches it when the Stripe provider is active and the user reaches the payment step.
const StripeCheckout = lazy(() => import('../components/StripeCheckout'))

const STEPS = ['Shipping', 'Payment', 'Review']

const computeTotal = (subtotal: number) => {
  const tax = subtotal * 0.1
  const shipping = subtotal >= 50 ? 0 : 5
  return subtotal + tax + shipping
}

export default function CheckoutPage() {
  const navigate = useNavigate()
  const {
    step,
    address,
    paymentMethodId,
    isFirstStep,
    isLastStep,
    canProceed,
    goNext,
    goBack,
    setAddress,
    setPaymentMethod,
    reset,
  } = useCheckout()

  const cartItems = useCartStore(selectCartItems)
  const subtotal = useCartStore(selectCartTotal)
  const userId = useAuthUserId()

  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const stripeEnabled = isStripeEnabled()
  // Stable draft order id for the PaymentIntent's idempotency/correlation, created once per session.
  const draftOrderId = useMemo(
    () => (stripeEnabled ? `draft-${crypto.randomUUID()}` : ''),
    [stripeEnabled]
  )

  const handleAddressValid = useCallback((addr: ShippingAddress) => setAddress(addr), [setAddress])

  // The shell gates /checkout behind auth; a null userId means an anomalous state (standalone
  // dev, expired session). Never place a real order without a real identity.
  if (!userId) {
    return (
      <Box sx={{ bgcolor: 'background.default', minHeight: '100vh' }}>
        <Container maxWidth="md" sx={{ px: { xs: 3, md: 4 }, py: { xs: 3, md: 5 } }}>
          <Typography variant="h4" fontWeight={700} sx={{ mb: { xs: 3, md: 4 } }}>
            Checkout
          </Typography>
          <Alert severity="warning" role="alert">
            Please sign in to complete checkout.
          </Alert>
        </Container>
      </Box>
    )
  }

  const handleNext = async () => {
    if (!isLastStep) {
      goNext()
      return
    }
    if (!address || !paymentMethodId) return
    // Re-read the live identity at submit time: the session may have expired since render.
    const liveUserId = window.__getAuthUserId?.() ?? null
    if (!liveUserId) {
      setSubmitError('Your session has expired. Please sign in again to place the order.')
      return
    }
    setIsSubmitting(true)
    setSubmitError(null)
    try {
      const order = await createOrder({
        userId: liveUserId,
        items: cartItems.map((i) => ({ productId: i.productId, quantity: i.quantity, price: i.price })),
        shippingAddress: address,
        paymentMethodId,
        totalAmount: computeTotal(subtotal),
      })
      reset()
      navigate(`confirmation/${order.id}`)
    } catch {
      setSubmitError('Failed to place order. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
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

          {step === 1 &&
            (stripeEnabled ? (
              <Suspense
                fallback={
                  <Box>
                    <Skeleton variant="rounded" height={48} sx={{ mb: 2 }} aria-label="Loading payment" />
                    <Skeleton variant="rounded" height={48} />
                  </Box>
                }
              >
                <StripeCheckout
                  orderId={draftOrderId}
                  userId={userId}
                  amount={computeTotal(subtotal)}
                  currency="USD"
                  onConfirmed={(paymentIntentId) => setPaymentMethod(paymentIntentId)}
                />
              </Suspense>
            ) : (
              <PaymentMethodForm onPaymentMethodReady={setPaymentMethod} />
            ))}

          {step === 2 && address && paymentMethodId && (
            <OrderReview address={address} paymentMethodId={paymentMethodId} />
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
        />
      </Container>
    </Box>
  )
}
