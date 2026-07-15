import { describe, it, expect, vi, afterEach } from 'vitest'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import { TEST_AUTH_USER_ID } from '../test/setup'
import { useCheckoutStore } from '../stores/checkoutStore'
import { useCartStore } from '../stores/cartStore'

const createOrder = vi.fn()

vi.mock('../api/orderService', () => ({
  createOrder: (...args: unknown[]) => createOrder(...args),
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => vi.fn() }
})

import CheckoutPage from './CheckoutPage'

// Order-first checkout (PR#122): the Review step (not Payment) is what submits the order, so
// seed only the address and land on step 1.
const seedReviewStep = () => {
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

afterEach(() => {
  act(() => useCheckoutStore.getState().reset())
  act(() => useCartStore.getState().clearCart())
  createOrder.mockReset()
})

describe('CheckoutPage — authentication gate', () => {
  it('should block checkout with a sign-in prompt when unauthenticated', () => {
    delete window.__getAuthUserId
    renderWithProviders(<CheckoutPage />)
    expect(screen.getByRole('alert')).toHaveTextContent(/sign in/i)
    expect(screen.queryByLabelText(/full name/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /next|continue to payment/i })).not.toBeInTheDocument()
  })

  it('should block checkout when the shell reports a signed-out user', () => {
    window.__getAuthUserId = () => null
    renderWithProviders(<CheckoutPage />)
    expect(screen.getByRole('alert')).toHaveTextContent(/sign in/i)
  })

  it('should render the checkout flow when authenticated', () => {
    renderWithProviders(<CheckoutPage />)
    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('should not submit an order when the session expires before submit', async () => {
    seedReviewStep()
    renderWithProviders(<CheckoutPage />)
    window.__getAuthUserId = () => null
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    expect(createOrder).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent(/sign in/i)
  })

  it('should submit the order with the Auth0 sub as userId, never "guest"', async () => {
    createOrder.mockResolvedValue({
      response: {
        orderId: 'order-123',
        orderNumber: 'ORD-1',
        status: 'PENDING',
        currency: 'USD',
        subtotal: 79.99,
        tax: 8,
        shippingCost: 5.99,
        discountAmount: null,
        loyaltyDiscount: null,
        total: 93.98,
        paymentIntentId: 'pi_1',
        clientSecret: 'secret_1',
        items: [],
      },
      isReplay: false,
    })
    seedReviewStep()
    renderWithProviders(<CheckoutPage />)
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(1))
    const payload = createOrder.mock.calls[0][0] as { userId: string }
    expect(payload.userId).toBe(TEST_AUTH_USER_ID)
    expect(payload.userId).not.toBe('guest')
  })
})
