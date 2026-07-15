import { describe, it, expect, vi, afterEach } from 'vitest'
import { act, screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import { useCheckoutStore } from '../stores/checkoutStore'
import { useCartStore } from '../stores/cartStore'

// Stub StripeCheckout so the test asserts the wiring (mount + onConfirmed) without pulling in
// Stripe.js. The button lets us simulate a confirmed PaymentIntent.
vi.mock('../components/StripeCheckout', () => ({
  default: ({ onConfirmed, orderId }: { onConfirmed: (id: string) => void; orderId: string }) => (
    <button data-testid="stripe-checkout" data-order-id={orderId} onClick={() => onConfirmed('pi_confirmed_1')}>
      stripe-checkout
    </button>
  ),
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => vi.fn() }
})

import CheckoutPage from './CheckoutPage'

const seedPaymentStep = () => {
  act(() => {
    useCartStore.getState().addItem({ productId: 'p1', name: 'Headphones', price: 79.99 })
    useCheckoutStore.getState().setStep(1)
    useCheckoutStore.getState().setAddress({
      fullName: 'Jane',
      line1: '123 St',
      city: 'SF',
      state: 'CA',
      postalCode: '94105',
      country: 'US',
    })
  })
}

describe('CheckoutPage — payment step', () => {
  afterEach(() => {
    act(() => useCheckoutStore.getState().reset())
    act(() => useCartStore.getState().clearCart())
  })

  it('should always render the Stripe checkout step (never a raw card form)', async () => {
    seedPaymentStep()
    renderWithProviders(<CheckoutPage />)
    // StripeCheckout is lazy-loaded (React.lazy + Suspense), so it resolves asynchronously.
    expect(await screen.findByTestId('stripe-checkout')).toBeInTheDocument()
    expect(screen.queryByLabelText(/card number/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/cvv/i)).not.toBeInTheDocument()
  })

  it('should set the payment method to the confirmed PaymentIntent id on Stripe confirmation', async () => {
    seedPaymentStep()
    const { default: userEvent } = await import('@testing-library/user-event')
    renderWithProviders(<CheckoutPage />)
    await userEvent.click(await screen.findByTestId('stripe-checkout'))
    expect(useCheckoutStore.getState().paymentMethodId).toBe('pi_confirmed_1')
  })
})
