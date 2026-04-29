import { describe, it, expect, beforeEach } from 'vitest'
import { useCheckoutStore } from './checkoutStore'

const mockAddress = {
  fullName: 'Jane Doe',
  line1: '123 Main St',
  line2: 'Apt 4',
  city: 'San Francisco',
  state: 'CA',
  postalCode: '94105',
  country: 'US',
}

describe('useCheckoutStore', () => {
  beforeEach(() => {
    useCheckoutStore.getState().reset()
  })

  it('should initialise with step 0, no address and no paymentMethodId', () => {
    const state = useCheckoutStore.getState()
    expect(state.step).toBe(0)
    expect(state.address).toBeNull()
    expect(state.paymentMethodId).toBeNull()
  })

  it('should set step when setStep is called', () => {
    useCheckoutStore.getState().setStep(2)
    expect(useCheckoutStore.getState().step).toBe(2)
  })

  it('should set address when setAddress is called', () => {
    useCheckoutStore.getState().setAddress(mockAddress)
    expect(useCheckoutStore.getState().address).toEqual(mockAddress)
  })

  it('should set paymentMethodId when setPaymentMethod is called', () => {
    useCheckoutStore.getState().setPaymentMethod('mock_card_12345')
    expect(useCheckoutStore.getState().paymentMethodId).toBe('mock_card_12345')
  })

  it('should reset all state to initial values', () => {
    useCheckoutStore.getState().setStep(2)
    useCheckoutStore.getState().setAddress(mockAddress)
    useCheckoutStore.getState().setPaymentMethod('mock_card_12345')

    useCheckoutStore.getState().reset()

    const state = useCheckoutStore.getState()
    expect(state.step).toBe(0)
    expect(state.address).toBeNull()
    expect(state.paymentMethodId).toBeNull()
  })

  it('should allow step to advance through all 3 checkout steps', () => {
    for (let i = 0; i <= 2; i++) {
      useCheckoutStore.getState().setStep(i)
      expect(useCheckoutStore.getState().step).toBe(i)
    }
  })
})
