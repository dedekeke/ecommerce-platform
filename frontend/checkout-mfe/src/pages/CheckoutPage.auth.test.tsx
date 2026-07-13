import { describe, it, expect, vi, afterEach } from 'vitest'
import { act, screen } from '@testing-library/react'
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

const seedReviewStep = () => {
  act(() => {
    useCartStore.getState().addItem({ productId: 'p1', name: 'Headphones', price: 79.99 })
    useCheckoutStore.getState().setStep(2)
    useCheckoutStore.getState().setAddress({
      fullName: 'Jane',
      line1: '123 St',
      city: 'SF',
      state: 'CA',
      postalCode: '94105',
      country: 'US',
    })
    useCheckoutStore.getState().setPaymentMethod('pi_confirmed_1')
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
    expect(screen.queryByRole('button', { name: /next|place order/i })).not.toBeInTheDocument()
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

  it('should place the order with the Auth0 sub as userId, never "guest"', async () => {
    createOrder.mockResolvedValue({ id: 'order-123' })
    seedReviewStep()
    renderWithProviders(<CheckoutPage />)
    await userEvent.click(screen.getByRole('button', { name: /place order/i }))
    expect(createOrder).toHaveBeenCalledTimes(1)
    const payload = createOrder.mock.calls[0][0] as { userId: string }
    expect(payload.userId).toBe(TEST_AUTH_USER_ID)
    expect(payload.userId).not.toBe('guest')
  })
})
