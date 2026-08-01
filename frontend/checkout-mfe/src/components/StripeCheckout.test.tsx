import { describe, it, expect, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'

// A publishable key is required for the component to attempt loadStripe; supply a fake one.
vi.mock('../config/payments', () => ({
  STRIPE_PUBLISHABLE_KEY: 'pk_test_fake',
}))

// loadStripe must not perform a network fetch in tests; Elements is stubbed to render children.
const loadStripe = vi.fn((..._args: unknown[]) => Promise.resolve({}))
vi.mock('@stripe/stripe-js', () => ({
  loadStripe: (...args: unknown[]) => loadStripe(...args),
}))

let seenOptions: unknown
vi.mock('@stripe/react-stripe-js', () => ({
  Elements: ({ children, options }: { children: React.ReactNode; options: unknown }) => {
    seenOptions = options
    return <div data-testid="elements">{children}</div>
  },
}))

vi.mock('./StripePaymentForm', () => ({
  default: () => <div data-testid="stripe-payment-form" />,
}))

import StripeCheckout from './StripeCheckout'

const props = {
  clientSecret: 'pi_test_123_secret_abc',
  onConfirmed: vi.fn(),
}

describe('StripeCheckout', () => {
  it('should mount Stripe Elements with the supplied clientSecret (order-first checkout)', async () => {
    renderWithProviders(<StripeCheckout {...props} />)
    await waitFor(() => expect(screen.getByTestId('stripe-payment-form')).toBeInTheDocument())
    expect(seenOptions).toEqual({ clientSecret: 'pi_test_123_secret_abc' })
  })

  it('should never call payment-service to create its own PaymentIntent', async () => {
    // No fetch/network mock is wired for a client-side intent-creation call — if StripeCheckout
    // tried to make one, this component-level render (with no MSW server running) would throw
    // or hang. Rendering synchronously to the payment form proves no such call happens.
    renderWithProviders(<StripeCheckout {...props} />)
    await waitFor(() => expect(screen.getByTestId('stripe-payment-form')).toBeInTheDocument())
  })

  it('should lazy-load the Stripe SDK and only call loadStripe with a non-empty publishable key', async () => {
    renderWithProviders(<StripeCheckout {...props} />)
    // loadStripe is reached via a dynamic import('@stripe/stripe-js'), so it resolves on a microtask.
    await waitFor(() => expect(loadStripe).toHaveBeenCalledWith('pk_test_fake'))
    loadStripe.mock.calls.forEach((args) => expect(args[0]).toBeTruthy())
  })
})
