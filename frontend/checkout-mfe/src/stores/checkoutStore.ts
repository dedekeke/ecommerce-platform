import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { devtools } from 'zustand/middleware'
import type { ShippingAddress } from '../api/types'

export interface CheckoutState {
  step: number
  address: ShippingAddress | null
  /**
   * Idempotency-Key sent on the order-creation POST. Stable across retries of the same checkout
   * attempt (e.g. a failed submit that the user retries, or a transient 502 that apiClient
   * retries automatically) so the backend can dedupe; a fresh key is minted on reset() so the
   * next checkout attempt does not collide with a completed order.
   */
  idempotencyKey: string
  /**
   * Email captured when an unauthenticated shopper chooses "continue as guest".
   * Non-null means the guest gate is satisfied and checkout submits to the guest
   * endpoint (POST /api/orders/guest); null means authenticated (or not yet past
   * the gate). Cleared on reset() so a later authenticated checkout is unaffected.
   */
  guestEmail: string | null
  setStep: (step: number) => void
  setAddress: (address: ShippingAddress) => void
  setGuestEmail: (email: string | null) => void
  reset: () => void
}

const createIdempotencyKey = () => crypto.randomUUID()

const initialState = {
  step: 0,
  address: null as ShippingAddress | null,
  idempotencyKey: createIdempotencyKey(),
  guestEmail: null as string | null,
}

export const useCheckoutStore = create<CheckoutState>()(
  devtools(
    persist(
      (set) => ({
        ...initialState,

        setStep: (step: number) => set({ step }, false, 'setStep'),

        setAddress: (address: ShippingAddress) => set({ address }, false, 'setAddress'),

        setGuestEmail: (guestEmail: string | null) =>
          set({ guestEmail }, false, 'setGuestEmail'),

        reset: () =>
          set({ ...initialState, idempotencyKey: createIdempotencyKey() }, false, 'reset'),
      }),
      {
        name: 'checkout-mfe-storage',
        storage: createJSONStorage(() => sessionStorage),
        partialize: (state) => ({
          step: state.step,
          address: state.address,
          idempotencyKey: state.idempotencyKey,
          guestEmail: state.guestEmail,
        }),
      }
    ),
    { name: 'CheckoutMFEStore' }
  )
)
