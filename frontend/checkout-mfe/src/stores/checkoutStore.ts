import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { devtools } from 'zustand/middleware'
import type { ShippingAddress } from '../api/types'

export interface CheckoutState {
  step: number
  address: ShippingAddress | null
  paymentMethodId: string | null
  setStep: (step: number) => void
  setAddress: (address: ShippingAddress) => void
  setPaymentMethod: (paymentMethodId: string) => void
  reset: () => void
}

const initialState = {
  step: 0,
  address: null as ShippingAddress | null,
  paymentMethodId: null as string | null,
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

        reset: () => set({ ...initialState }, false, 'reset'),
      }),
      {
        name: 'checkout-mfe-storage',
        storage: createJSONStorage(() => sessionStorage),
        partialize: (state) => ({
          step: state.step,
          address: state.address,
          paymentMethodId: state.paymentMethodId,
        }),
      }
    ),
    { name: 'CheckoutMFEStore' }
  )
)
