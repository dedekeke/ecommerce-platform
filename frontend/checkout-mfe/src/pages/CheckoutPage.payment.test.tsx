import { describe, it, expect, vi, beforeAll, afterAll, afterEach, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers } from '../test/mocks/handlers'
import { renderWithProviders } from '../test/renderWithProviders'
import { useCheckoutStore } from '../stores/checkoutStore'
import { useCartStore } from '../stores/cartStore'

const API_BASE = 'http://localhost:8080/api'
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
  return { ...actual, useNavigate: () => mockNavigate }
})

// Stub StripeCheckout to capture exactly what clientSecret it was mounted with, proving it came
// from the order response rather than a self-created PaymentIntent.
vi.mock('../components/StripeCheckout', () => ({
  default: ({ onConfirmed, clientSecret }: { onConfirmed: (id: string) => void; clientSecret: string }) => (
    <button data-testid="stripe-checkout" data-client-secret={clientSecret} onClick={() => onConfirmed('pi_confirmed_1')}>
      stripe-checkout
    </button>
  ),
}))

import CheckoutPage from './CheckoutPage'

const fillShippingAndReachReview = async () => {
  await userEvent.type(screen.getByLabelText(/full name/i), 'Jane Doe')
  await userEvent.type(screen.getByLabelText(/address line 1/i), '123 Main St')
  await userEvent.type(screen.getByLabelText(/city/i), 'San Francisco')
  await userEvent.type(screen.getByLabelText(/state/i), 'CA')
  await userEvent.type(screen.getByLabelText(/postal code/i), '94105')
  await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).toBeEnabled())
  await userEvent.click(screen.getByRole('button', { name: /next/i }))
  await waitFor(() => expect(screen.getByText(/review your order/i)).toBeInTheDocument())
}

describe('CheckoutPage — order-first payment hand-off', () => {
  beforeEach(() => {
    useCartStore.getState().addItem({ productId: 'p1', name: 'Headphones', price: 79.99 })
    mockNavigate.mockReset()
  })

  it('should mount StripeCheckout with the clientSecret from a fresh (201) order response', async () => {
    renderWithProviders(<CheckoutPage />)
    await fillShippingAndReachReview()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    const stripeCheckout = await screen.findByTestId('stripe-checkout')
    expect(stripeCheckout).toHaveAttribute('data-client-secret', 'pi_test_123_secret_abc')
  })

  it('should navigate to confirmation with the returned PaymentIntent id on Stripe confirmation', async () => {
    renderWithProviders(<CheckoutPage />)
    await fillShippingAndReachReview()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    await userEvent.click(await screen.findByTestId('stripe-checkout'))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('confirmation/order-123'))
  })

  it('should toast an "Order placed successfully!" success message on Stripe confirmation', async () => {
    window.__ecommerceToastHost = true
    const listener = vi.fn()
    window.addEventListener('ecommerce:toast', listener)

    renderWithProviders(<CheckoutPage />)
    await fillShippingAndReachReview()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    await userEvent.click(await screen.findByTestId('stripe-checkout'))
    await waitFor(() => expect(listener).toHaveBeenCalledOnce())

    const event = listener.mock.calls[0][0] as CustomEvent
    expect(event.detail).toMatchObject({ type: 'success', message: 'Order placed successfully!' })

    window.removeEventListener('ecommerce:toast', listener)
    delete window.__ecommerceToastHost
  })

  it('should advance to the Payment step on a 200 replay that still carries a clientSecret', async () => {
    server.use(
      http.post(`${API_BASE}/orders`, () =>
        HttpResponse.json(
          {
            orderId: 'order-replay-1',
            orderNumber: 'ORD-REPLAY-1',
            status: 'PENDING',
            currency: 'USD',
            subtotal: 79.99,
            tax: 8,
            shippingCost: 5.99,
            discountAmount: null,
            loyaltyDiscount: null,
            total: 93.98,
            paymentIntentId: 'pi_replay_1',
            clientSecret: 'secret_replay_1',
            items: [],
          },
          { status: 200 }
        )
      )
    )
    renderWithProviders(<CheckoutPage />)
    await fillShippingAndReachReview()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    const stripeCheckout = await screen.findByTestId('stripe-checkout')
    expect(stripeCheckout).toHaveAttribute('data-client-secret', 'secret_replay_1')
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('should skip the Payment step and route straight to the order-status view on a 200 replay with a null clientSecret', async () => {
    server.use(
      http.post(`${API_BASE}/orders`, () =>
        HttpResponse.json(
          {
            orderId: 'order-replay-2',
            orderNumber: 'ORD-REPLAY-2',
            status: 'PENDING',
            currency: 'USD',
            subtotal: 79.99,
            tax: 8,
            shippingCost: 5.99,
            discountAmount: null,
            loyaltyDiscount: null,
            total: 93.98,
            paymentIntentId: 'pi_replay_2',
            clientSecret: null,
            items: [],
          },
          { status: 200 }
        )
      )
    )
    renderWithProviders(<CheckoutPage />)
    await fillShippingAndReachReview()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('confirmation/order-replay-2'))
    expect(screen.queryByTestId('stripe-checkout')).not.toBeInTheDocument()
  })
})
