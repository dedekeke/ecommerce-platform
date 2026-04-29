import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import OrderConfirmation from './OrderConfirmation'

const mockOrder = {
  id: 'order-123',
  orderNumber: 'ORD-20260429-001',
  status: 'PENDING' as const,
  items: [],
  shippingAddress: {
    fullName: 'Jane Doe',
    line1: '123 Main St',
    city: 'San Francisco',
    state: 'CA',
    postalCode: '94105',
    country: 'US',
  },
  totalAmount: 168.97,
  createdAt: '2026-04-29T00:00:00.000Z',
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
