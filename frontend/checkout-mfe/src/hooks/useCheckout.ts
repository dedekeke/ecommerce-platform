import { useCheckoutStore } from '../stores/checkoutStore'
import type { ShippingAddress } from '../api/types'

const TOTAL_STEPS = 3

export function useCheckout() {
  const step = useCheckoutStore((s) => s.step)
  const address = useCheckoutStore((s) => s.address)
  const paymentMethodId = useCheckoutStore((s) => s.paymentMethodId)
  const idempotencyKey = useCheckoutStore((s) => s.idempotencyKey)
  const setStep = useCheckoutStore((s) => s.setStep)
  const setAddress = useCheckoutStore((s) => s.setAddress)
  const setPaymentMethod = useCheckoutStore((s) => s.setPaymentMethod)
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
    if (step === 1) return paymentMethodId !== null
    return true
  })()

  return {
    step,
    address,
    paymentMethodId,
    idempotencyKey,
    isFirstStep,
    isLastStep,
    canProceed,
    goNext,
    goBack,
    goToStep,
    setAddress: (addr: ShippingAddress) => setAddress(addr),
    setPaymentMethod,
    reset,
  }
}
