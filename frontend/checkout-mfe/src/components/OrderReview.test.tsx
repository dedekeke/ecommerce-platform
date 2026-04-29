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
    renderWithProviders(
      <OrderReview address={mockAddress} paymentMethodId="mock_card_123" />
    )
    expect(screen.getByText('Jane Doe')).toBeInTheDocument()
    expect(screen.getByText('123 Main St')).toBeInTheDocument()
    expect(screen.getByText(/San Francisco, CA/)).toBeInTheDocument()
    expect(screen.getByText('94105', { exact: false })).toBeInTheDocument()
  })

  it('should display payment method (masked)', () => {
    renderWithProviders(
      <OrderReview address={mockAddress} paymentMethodId="mock_card_123" />
    )
    expect(screen.getByText(/card/i)).toBeInTheDocument()
  })

  it('should show empty state when cart has no items', () => {
    renderWithProviders(
      <OrderReview address={mockAddress} paymentMethodId="mock_card_123" />
    )
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
    renderWithProviders(
      <OrderReview address={mockAddress} paymentMethodId="mock_card_123" />
    )
    expect(screen.getByText('Wireless Headphones')).toBeInTheDocument()
    expect(screen.getByText('Mechanical Keyboard')).toBeInTheDocument()
  })

  it('should compute subtotal, tax (10%) and shipping ($5 under $50 threshold)', () => {
    useCartStore.getState().addItem({ productId: 'prod-1', name: 'Item A', price: 20 })
    renderWithProviders(
      <OrderReview address={mockAddress} paymentMethodId="mock_card_123" />
    )
    // subtotal appears in both the item table row and the totals summary
    expect(screen.getAllByText('$20.00').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText('$2.00')).toBeInTheDocument()
    expect(screen.getByText('$5.00')).toBeInTheDocument()
    expect(screen.getByText('$27.00')).toBeInTheDocument()
  })

  it('should show free shipping when subtotal >= $50', () => {
    useCartStore.getState().addItem({ productId: 'prod-1', name: 'Item A', price: 50 })
    renderWithProviders(
      <OrderReview address={mockAddress} paymentMethodId="mock_card_123" />
    )
    expect(screen.getByText(/free/i)).toBeInTheDocument()
  })

  it('should display PayPal payment method when paymentMethodId starts with mock_paypal', () => {
    renderWithProviders(
      <OrderReview address={mockAddress} paymentMethodId="mock_paypal_12345" />
    )
    expect(screen.getByText(/paypal/i)).toBeInTheDocument()
  })
})
