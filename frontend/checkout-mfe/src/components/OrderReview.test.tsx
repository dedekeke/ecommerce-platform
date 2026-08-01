import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import OrderReview from './OrderReview'
import { useCartStore } from '../stores/cartStore'
import type { ShippingAddress } from '../api/types'

const mockAddress: ShippingAddress = {
  fullName: 'Jane Doe',
  line1: '123 Main St',
  line2: 'Apt 4',
  city: 'San Francisco',
  state: 'CA',
  postalCode: '94105',
  country: 'US',
}

describe('OrderReview', () => {
  beforeEach(() => {
    useCartStore.getState().clearCart()
  })

  it('should display shipping address details', () => {
    renderWithProviders(<OrderReview address={mockAddress} />)
    expect(screen.getByText('Jane Doe')).toBeInTheDocument()
    expect(screen.getByText('123 Main St')).toBeInTheDocument()
    expect(screen.getByText(/San Francisco, CA/)).toBeInTheDocument()
    expect(screen.getByText('94105', { exact: false })).toBeInTheDocument()
  })

  it('should indicate that payment happens on the next step (order-first checkout)', () => {
    renderWithProviders(<OrderReview address={mockAddress} />)
    expect(screen.getByText(/next step/i)).toBeInTheDocument()
  })

  it('should show empty state when cart has no items', () => {
    renderWithProviders(<OrderReview address={mockAddress} />)
    expect(screen.getByText(/no items in cart/i)).toBeInTheDocument()
  })

  it('should render cart line items', () => {
    useCartStore.getState().addItem({
      productId: 'prod-1',
      name: 'Wireless Headphones',
      price: 79.99,
    })
    useCartStore.getState().addItem({
      productId: 'prod-2',
      name: 'Mechanical Keyboard',
      price: 149.99,
    })
    renderWithProviders(<OrderReview address={mockAddress} />)
    expect(screen.getByText('Wireless Headphones')).toBeInTheDocument()
    expect(screen.getByText('Mechanical Keyboard')).toBeInTheDocument()
  })

  it('should compute subtotal, tax (10%) and shipping ($5 under $50 threshold)', () => {
    useCartStore.getState().addItem({ productId: 'prod-1', name: 'Item A', price: 20 })
    renderWithProviders(<OrderReview address={mockAddress} />)
    // subtotal appears in both the item table row and the totals summary
    expect(screen.getAllByText('$20.00').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('$2.00')).toBeInTheDocument()
    expect(screen.getByText('$5.00')).toBeInTheDocument()
    expect(screen.getByText('$27.00')).toBeInTheDocument()
  })

  it('should show free shipping when subtotal >= $50', () => {
    useCartStore.getState().addItem({ productId: 'prod-1', name: 'Item A', price: 50 })
    renderWithProviders(<OrderReview address={mockAddress} />)
    expect(screen.getByText(/free/i)).toBeInTheDocument()
  })

  it('should never render a raw card number or CVV field', () => {
    renderWithProviders(<OrderReview address={mockAddress} />)
    expect(screen.queryByLabelText(/card number/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/cvv/i)).not.toBeInTheDocument()
  })

  it('should not show a discount line when no promotion is applied', () => {
    useCartStore.getState().addItem({ productId: 'prod-1', name: 'Item A', price: 20 })
    renderWithProviders(<OrderReview address={mockAddress} />)
    expect(screen.queryByText(/discount/i)).not.toBeInTheDocument()
  })

  // Promo fields arrive via the shared `cart-storage` handoff from cart-mfe (see
  // cartStore.ts partialize) — set directly here rather than via a checkout-mfe action.
  it('should show a discount line and recompute tax/total off the discounted subtotal', () => {
    useCartStore.getState().addItem({ productId: 'prod-1', name: 'Item A', price: 20 })
    useCartStore.setState({ promotionCode: 'SAVE5', discountAmount: 5, promotionName: '$5 off' })
    renderWithProviders(<OrderReview address={mockAddress} />)

    expect(screen.getByText(/discount \(save5\)/i)).toBeInTheDocument()
    expect(screen.getByText('-$5.00')).toBeInTheDocument()
    // discounted subtotal = 15, tax (10%) = 1.50, shipping = $5 (under $50 threshold), total = 21.50
    expect(screen.getByText('$1.50')).toBeInTheDocument()
    expect(screen.getByText('$21.50')).toBeInTheDocument()
  })

  it('should cap the discount at the subtotal so totals never go negative', () => {
    useCartStore.getState().addItem({ productId: 'prod-1', name: 'Item A', price: 10 })
    useCartStore.setState({ promotionCode: 'BIGSAVE', discountAmount: 999, promotionName: 'Huge discount' })
    renderWithProviders(<OrderReview address={mockAddress} />)

    expect(screen.getByText('-$10.00')).toBeInTheDocument()
    // discounted subtotal = 0, tax = 0, shipping = $5, total = 5 (shipping and total both render as $5.00)
    expect(screen.getByText('$0.00')).toBeInTheDocument()
    expect(screen.getAllByText('$5.00')).toHaveLength(2)
  })
})
