import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { devtools } from 'zustand/middleware'
import type { ShippingAddress } from '../api/types'

export interface CheckoutState {
  step: number
  address: ShippingAddress | null
  paymentMethodId: string | null
  /**
   * Idempotency-Key sent on the order-creation POST. Stable across retries of the same checkout
   * attempt (e.g. a failed submit that the user retries) so the backend can dedupe; a fresh key
   * is minted on reset() so the next checkout attempt does not collide with a completed order.
   */
  idempotencyKey: string
  setStep: (step: number) => void
  setAddress: (address: ShippingAddress) => void
  setPaymentMethod: (paymentMethodId: string) => void
  reset: () => void
}

const createIdempotencyKey = () => crypto.randomUUID()

const initialState = {
  step: 0,
  address: null as ShippingAddress | null,
  paymentMethodId: null as string | null,
  idempotencyKey: createIdempotencyKey(),
}

export const useCheckoutStore = create<CheckoutState>()(
  devtools(
    persist(
      (set) => ({
        ...initialState,

        setStep: (step: number) => set({ step }, false, 'setStep'),

        setAddress: (address: ShippingAddress) => set({ address }, false, 'setAddress'),

        setPaymentMethod: (paymentMethodId: string) =>
          set({ paymentMethodId }, false, 'setPaymentMethod'),

        reset: () =>
          set({ ...initialState, idempotencyKey: createIdempotencyKey() }, false, 'reset'),
      }),
      {
        name: 'checkout-mfe-storage',
        storage: createJSONStorage(() => sessionStorage),
        partialize: (state) => ({
          step: state.step,
          address: state.address,
          paymentMethodId: state.paymentMethodId,
          idempotencyKey: state.idempotencyKey,
        }),
      }
    ),
    { name: 'CheckoutMFEStore' }
  )
)
