import { describe, it, expect, beforeEach, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from './test/renderWithProviders'
import { useCartStore } from './stores/cartStore'
import { useCheckoutStore } from './stores/checkoutStore'
import Checkout from './Checkout'

vi.mock('./api/orderService', () => ({
  getOrder: vi.fn().mockResolvedValue({
    id: 'order-123',
    orderNumber: 'ORD-2024-00123',
    status: 'CONFIRMED',
    items: [],
    shippingAddress: {
      fullName: 'Test User',
      line1: '123 Main St',
      city: 'Anytown',
      state: 'CA',
      postalCode: '12345',
      country: 'US',
    },
    totalAmount: 0,
    createdAt: new Date().toISOString(),
  }),
  createOrder: vi.fn(),
}))

describe('Checkout (federated entry)', () => {
  beforeEach(() => {
    useCartStore.getState().clearCart()
    useCheckoutStore.getState().reset()
  })

  it('should render without ThemeProvider wrapper', () => {
    renderWithProviders(<Checkout />)
    expect(screen.getByRole('heading', { name: /checkout/i })).toBeInTheDocument()
  })

  it('should render the checkout stepper at the index route', () => {
    renderWithProviders(<Checkout />, { initialEntries: ['/'] })
    expect(screen.getByRole('heading', { name: /checkout/i })).toBeInTheDocument()
  })

  it('should render the order confirmed heading at /confirmation/:orderId', async () => {
    renderWithProviders(<Checkout />, {
      initialEntries: ['/confirmation/order-123'],
    })
    await waitFor(() => {
      expect(screen.getByText(/order confirmed/i)).toBeInTheDocument()
    })
  })
})
