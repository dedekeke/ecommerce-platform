import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'

const createPaymentIntent = vi.fn()

vi.mock('../api/paymentService', () => ({
  createPaymentIntent: (...args: unknown[]) => createPaymentIntent(...args),
}))

// A publishable key is required for the component to attempt loadStripe; supply a fake one.
vi.mock('../config/payments', () => ({
  STRIPE_PUBLISHABLE_KEY: 'pk_test_fake',
}))

// loadStripe must not perform a network fetch in tests; Elements is stubbed to render children.
const loadStripe = vi.fn((..._args: unknown[]) => Promise.resolve({}))
vi.mock('@stripe/stripe-js', () => ({
  loadStripe: (...args: unknown[]) => loadStripe(...args),
}))

vi.mock('@stripe/react-stripe-js', () => ({
  Elements: ({ children }: { children: React.ReactNode }) => <div data-testid="elements">{children}</div>,
}))

vi.mock('./StripePaymentForm', () => ({
  default: () => <div data-testid="stripe-payment-form" />,
}))

import StripeCheckout from './StripeCheckout'

const props = {
  orderId: 'order-1',
  userId: 'user-1',
  amount: 42,
  currency: 'USD',
  onConfirmed: vi.fn(),
}

describe('StripeCheckout', () => {
  beforeEach(() => {
    createPaymentIntent.mockReset()
  })

  it('should show a loading skeleton while fetching the client secret', () => {
    createPaymentIntent.mockReturnValue(new Promise(() => {}))
    renderWithProviders(<StripeCheckout {...props} />)
    expect(screen.getByLabelText(/loading payment form/i)).toBeInTheDocument()
  })

  it('should render the Stripe payment form once the client secret is fetched', async () => {
    createPaymentIntent.mockResolvedValue({
      paymentId: 1,
      paymentIntentId: 'pi_test_123',
      clientSecret: 'pi_test_123_secret_abc',
      status: 'PENDING',
    })
    renderWithProviders(<StripeCheckout {...props} />)
    await waitFor(() => expect(screen.getByTestId('stripe-payment-form')).toBeInTheDocument())
    expect(createPaymentIntent).toHaveBeenCalledWith({
      orderId: 'order-1',
      userId: 'user-1',
      amount: 42,
      currency: 'USD',
    })
  })

  it('should show an error when intent creation fails', async () => {
    createPaymentIntent.mockRejectedValue(new Error('boom'))
    renderWithProviders(<StripeCheckout {...props} />)
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/unable to start payment/i))
  })

  it('should only ever call loadStripe with a non-empty publishable key', () => {
    createPaymentIntent.mockReturnValue(new Promise(() => {}))
    renderWithProviders(<StripeCheckout {...props} />)
    expect(loadStripe).toHaveBeenCalledWith('pk_test_fake')
    loadStripe.mock.calls.forEach((args) => expect(args[0]).toBeTruthy())
  })
})
