import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import OrderConfirmation from './OrderConfirmation'

// Mirrors the real order-service OrderResponse (PR#139/#145) — orderId/total, not id/totalAmount.
const mockOrder = {
  orderId: 'order-123',
  orderNumber: 'ORD-20260429-001',
  status: 'PENDING' as const,
  currency: 'USD',
  subtotal: 159.98,
  tax: 8.0,
  shippingCost: 0.99,
  discountAmount: null,
  loyaltyDiscount: null,
  total: 168.97,
  items: [
    { productId: 'prod-1', productName: 'Headphones', price: 79.99, quantity: 2, subtotal: 159.98 },
  ],
  shippingAddress: {
    street: '123 Main St',
    city: 'San Francisco',
    state: 'CA',
    postalCode: '94105',
    country: 'US',
  },
  paymentIntentId: 'pi_test_123',
  guestOrder: false,
  carrier: null,
  trackingNumber: null,
  shippedAt: null,
  deliveredAt: null,
  createdAt: '2026-04-29T00:00:00.000Z',
  updatedAt: '2026-04-29T00:00:00.000Z',
}

describe('OrderConfirmation', () => {
  it('should display the order number', () => {
    renderWithProviders(<OrderConfirmation order={mockOrder} />)
    expect(screen.getByText('ORD-20260429-001')).toBeInTheDocument()
  })

  it('should display a success heading', () => {
    renderWithProviders(<OrderConfirmation order={mockOrder} />)
    expect(screen.getByRole('heading', { name: /order confirmed/i })).toBeInTheDocument()
  })

  it('should display the ETA copy', () => {
    renderWithProviders(<OrderConfirmation order={mockOrder} />)
    expect(screen.getByText(/estimated delivery/i)).toBeInTheDocument()
  })

  it('should render a "View order" link pointing to the correct route', () => {
    renderWithProviders(<OrderConfirmation order={mockOrder} />)
    const link = screen.getByRole('link', { name: /view order/i })
    expect(link).toHaveAttribute('href', '/orders/order-123')
  })

  it('should display the total amount', () => {
    renderWithProviders(<OrderConfirmation order={mockOrder} />)
    expect(screen.getByText(/168\.97/)).toBeInTheDocument()
  })

  it('should display a "Continue shopping" button', () => {
    renderWithProviders(<OrderConfirmation order={mockOrder} />)
    expect(screen.getByRole('link', { name: /continue shopping/i })).toBeInTheDocument()
  })
})
