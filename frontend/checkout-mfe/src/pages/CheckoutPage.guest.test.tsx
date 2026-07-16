import { describe, it, expect, vi, afterEach } from 'vitest'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import { useCheckoutStore } from '../stores/checkoutStore'
import { useCartStore } from '../stores/cartStore'

const createOrder = vi.fn()
const createGuestOrder = vi.fn()

vi.mock('../api/orderService', () => ({
  createOrder: (...args: unknown[]) => createOrder(...args),
  createGuestOrder: (...args: unknown[]) => createGuestOrder(...args),
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => vi.fn() }
})

import CheckoutPage from './CheckoutPage'

const seedReviewStep = () => {
  act(() => {
    useCartStore.getState().addItem({ productId: 'p1', name: 'Headphones', price: 79.99 })
    useCheckoutStore.getState().setStep(1)
    useCheckoutStore.getState().setAddress({
      fullName: 'Guest Buyer',
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
  createGuestOrder.mockReset()
})

describe('CheckoutPage — guest checkout', () => {
  it('should reveal the checkout form after continuing as guest', async () => {
    delete window.__getAuthUserId
    renderWithProviders(<CheckoutPage />)

    await userEvent.type(screen.getByLabelText(/email address/i), 'guest@example.com')
    await userEvent.click(screen.getByRole('button', { name: /continue as guest/i }))

    // The address form (step 0) is now shown; the gate is gone.
    expect(await screen.findByLabelText(/full name/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /continue as guest/i })).not.toBeInTheDocument()
  })

  it('should submit to the guest endpoint (not the authenticated one) at review', async () => {
    delete window.__getAuthUserId
    createGuestOrder.mockResolvedValue({
      response: {
        orderId: 'guest-order-123',
        orderNumber: 'ORD-1',
        status: 'PENDING',
        currency: 'USD',
        subtotal: 79.99,
        tax: 8,
        shippingCost: 5.99,
        discountAmount: null,
        loyaltyDiscount: null,
        total: 93.98,
        paymentIntentId: 'pi_guest',
        clientSecret: 'secret_guest',
        items: [],
      },
      isReplay: false,
    })
    // Past the gate + on the review step.
    act(() => useCheckoutStore.getState().setGuestEmail('guest@example.com'))
    seedReviewStep()
    renderWithProviders(<CheckoutPage />)

    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    await waitFor(() => expect(createGuestOrder).toHaveBeenCalledTimes(1))
    expect(createOrder).not.toHaveBeenCalled()
    const [payload] = createGuestOrder.mock.calls[0] as [{ email: string; shippingAddress: unknown }]
    expect(payload.email).toBe('guest@example.com')
    expect(payload.shippingAddress).toBeTruthy()
    // A guest payload must never carry a userId.
    expect((payload as Record<string, unknown>).userId).toBeUndefined()
  })

  it('should prefer the authenticated create when a session is present', async () => {
    // Authenticated by default (setup sets __getAuthUserId). Even if a stale guest
    // email lingered, a live session wins.
    createOrder.mockResolvedValue({
      response: { orderId: 'o1', orderNumber: 'ORD-1', status: 'PENDING', currency: 'USD', subtotal: 0, tax: 0, shippingCost: 0, discountAmount: null, loyaltyDiscount: null, total: 1, paymentIntentId: 'pi', clientSecret: 'sec', items: [] },
      isReplay: false,
    })
    act(() => useCheckoutStore.getState().setGuestEmail('stale@example.com'))
    seedReviewStep()
    renderWithProviders(<CheckoutPage />)

    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(1))
    expect(createGuestOrder).not.toHaveBeenCalled()
  })
})
