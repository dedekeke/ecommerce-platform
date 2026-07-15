import { describe, it, expect, vi, beforeAll, afterAll, afterEach, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { handlers } from '../test/mocks/handlers'
import { renderWithProviders } from '../test/renderWithProviders'
import CheckoutPage from './CheckoutPage'
import { useCheckoutStore } from '../stores/checkoutStore'
import { useCartStore } from '../stores/cartStore'

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => {
  server.resetHandlers()
  useCheckoutStore.getState().reset()
  useCartStore.getState().clearCart()
})
afterAll(() => server.close())

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Stub StripeCheckout so the payment step doesn't require the real Stripe.js SDK; the button
// lets tests simulate a confirmed PaymentIntent without ever touching raw card data. Order
// creation (POST /orders, order-first checkout — PR#122) still goes through real MSW handlers.
vi.mock('../components/StripeCheckout', () => ({
  default: ({ onConfirmed, clientSecret }: { onConfirmed: (id: string) => void; clientSecret: string }) => (
    <div>
      <p>Payment step (secret: {clientSecret})</p>
      <button onClick={() => onConfirmed('pi_confirmed_test')}>Confirm payment (test stub)</button>
    </div>
  ),
}))

const fillShippingForm = async () => {
  await userEvent.type(screen.getByLabelText(/full name/i), 'Jane Doe')
  await userEvent.type(screen.getByLabelText(/address line 1/i), '123 Main St')
  await userEvent.type(screen.getByLabelText(/city/i), 'San Francisco')
  await userEvent.type(screen.getByLabelText(/state/i), 'CA')
  await userEvent.type(screen.getByLabelText(/postal code/i), '94105')
}

const goToReviewStep = async () => {
  await fillShippingForm()
  await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).toBeEnabled())
  await userEvent.click(screen.getByRole('button', { name: /next/i }))
  await waitFor(() => expect(screen.getByText(/review your order/i)).toBeInTheDocument())
}

describe('CheckoutPage', () => {
  beforeEach(() => {
    useCartStore.getState().addItem({ productId: 'prod-1', name: 'Headphones', price: 79.99 })
    mockNavigate.mockReset()
  })

  it('should render the Shipping step by default', () => {
    renderWithProviders(<CheckoutPage />)
    expect(screen.getByText(/shipping address/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument()
  })

  it('should show all 3 stepper labels', () => {
    renderWithProviders(<CheckoutPage />)
    expect(screen.getByText('Shipping')).toBeInTheDocument()
    expect(screen.getByText('Payment')).toBeInTheDocument()
    expect(screen.getByText('Review')).toBeInTheDocument()
  })

  it('should keep Next disabled until shipping form is valid', async () => {
    renderWithProviders(<CheckoutPage />)
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
    await fillShippingForm()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /next/i })).toBeEnabled()
    })
  })

  it('should advance to the Review step (not Payment) when Next is clicked on valid shipping', async () => {
    renderWithProviders(<CheckoutPage />)
    await goToReviewStep()
    expect(screen.getByText(/review your order/i)).toBeInTheDocument()
  })

  it('should go back to Shipping step when Back is clicked on the Review step', async () => {
    renderWithProviders(<CheckoutPage />)
    await goToReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /back/i }))
    await waitFor(() => {
      expect(screen.getByLabelText(/full name/i)).toBeInTheDocument()
    })
  })

  it('should submit the order (POST /orders) and advance to the Payment step when confirming Review', async () => {
    renderWithProviders(<CheckoutPage />)
    await goToReviewStep()

    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    await waitFor(() => {
      expect(screen.getByText(/payment step/i)).toBeInTheDocument()
    })
    // The clientSecret came from the order response, not a client-created PaymentIntent.
    expect(screen.getByText(/pi_test_123_secret_abc/)).toBeInTheDocument()
  })

  it('should never render a raw card number, expiry or CVV field at any step', async () => {
    renderWithProviders(<CheckoutPage />)
    await goToReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(() => expect(screen.getByText(/payment step/i)).toBeInTheDocument())

    expect(screen.queryByLabelText(/card number/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/^expiry/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/cvv/i)).not.toBeInTheDocument()
  })

  it('should navigate to the confirmation page once Stripe confirms payment', async () => {
    renderWithProviders(<CheckoutPage />)
    await goToReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(() => expect(screen.getByText(/payment step/i)).toBeInTheDocument())

    await userEvent.click(screen.getByRole('button', { name: /confirm payment/i }))

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('confirmation/order-123'))
  })
})
