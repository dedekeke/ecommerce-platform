import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from './test/renderWithProviders'
import { useCartStore } from './stores/cartStore'
import Cart from './Cart'

describe('Cart (federated entry)', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
  })

  it('should render without ThemeProvider wrapper', () => {
    renderWithProviders(<Cart />)
    expect(screen.getByRole('heading', { name: /shopping cart/i })).toBeInTheDocument()
  })

  it('should render cart page at the index route', () => {
    renderWithProviders(<Cart />, { initialEntries: ['/'] })
    expect(screen.getByRole('heading', { name: /shopping cart/i })).toBeInTheDocument()
  })

  it('should render empty cart state by default', () => {
    renderWithProviders(<Cart />)
    expect(screen.getByRole('heading', { name: /your cart is empty/i })).toBeInTheDocument()
  })
})
