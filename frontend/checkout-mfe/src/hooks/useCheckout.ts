import { useCheckoutStore } from '../stores/checkoutStore'
import type { ShippingAddress } from '../api/types'

const TOTAL_STEPS = 3

/**
 * Step order is Shipping -> Review -> Payment (order-first checkout, PR#122): the Review step's
 * "Continue to payment" action is what submits `POST /api/orders`, since the PaymentIntent
 * (and its `clientSecret`) only exists once the order-creation saga has run. The Payment step
 * has no generic "Next" action — Stripe's own confirm button drives completion — so canProceed
 * is always false there; CheckoutPage hides the stepper's action bar on that step.
 */
export function useCheckout() {
  const step = useCheckoutStore((s) => s.step)
  const address = useCheckoutStore((s) => s.address)
  const idempotencyKey = useCheckoutStore((s) => s.idempotencyKey)
  const setStep = useCheckoutStore((s) => s.setStep)
  const setAddress = useCheckoutStore((s) => s.setAddress)
  const reset = useCheckoutStore((s) => s.reset)

  const isFirstStep = step === 0
  const isLastStep = step === TOTAL_STEPS - 1

  const goNext = () => {
    if (step < TOTAL_STEPS - 1) setStep(step + 1)
  }

  const goBack = () => {
    if (step > 0) setStep(step - 1)
  }

  const goToStep = (target: number) => {
    if (target >= 0 && target < TOTAL_STEPS) setStep(target)
  }

  const canProceed = (() => {
    if (step === 0) return address !== null
    if (step === 1) return true
    return false
  })()

  return {
    step,
    address,
    idempotencyKey,
    isFirstStep,
    isLastStep,
    canProceed,
    goNext,
    goBack,
    goToStep,
    setAddress: (addr: ShippingAddress) => setAddress(addr),
    reset,
  }
}
