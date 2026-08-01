import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import { useCartStore } from '../stores/cartStore'
import CartPage from './CartPage'

describe('CartPage', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
  })

  it('should render the page heading', () => {
    renderWithProviders(<CartPage />)
    expect(screen.getByRole('heading', { name: /shopping cart/i })).toBeInTheDocument()
  })

  it('should render empty cart state when store is empty', () => {
    renderWithProviders(<CartPage />)
    expect(screen.getByRole('heading', { name: /your cart is empty/i })).toBeInTheDocument()
  })

  it('should render cart items from the store', () => {
    useCartStore.setState({
      items: [
        { productId: 'p1', name: 'Wireless Headphones', price: 79.99, quantity: 2 },
      ],
      total: 159.98,
      itemCount: 2,
    })
    renderWithProviders(<CartPage />)
    expect(screen.getByText('Wireless Headphones')).toBeInTheDocument()
  })

  it('should render CartSummary when there are items', () => {
    useCartStore.setState({
      items: [{ productId: 'p1', name: 'Widget', price: 10.0, quantity: 1 }],
      total: 10.0,
      itemCount: 1,
    })
    renderWithProviders(<CartPage />)
    expect(screen.getByRole('button', { name: /proceed to checkout/i })).toBeInTheDocument()
  })

  it('should not render CartSummary when cart is empty', () => {
    renderWithProviders(<CartPage />)
    expect(
      screen.queryByRole('button', { name: /proceed to checkout/i })
    ).not.toBeInTheDocument()
  })

  it('should display singular "item" label when cart has exactly one item', () => {
    useCartStore.setState({
      items: [{ productId: 'p1', name: 'Widget', price: 10.0, quantity: 1 }],
      total: 10.0,
      itemCount: 1,
    })
    renderWithProviders(<CartPage />)
    expect(screen.getByText('(1 item)')).toBeInTheDocument()
  })

  it('should display plural "items" label when cart has multiple items', () => {
    useCartStore.setState({
      items: [
        { productId: 'p1', name: 'Widget', price: 10.0, quantity: 1 },
        { productId: 'p2', name: 'Gadget', price: 20.0, quantity: 1 },
      ],
      total: 30.0,
      itemCount: 2,
    })
    renderWithProviders(<CartPage />)
    expect(screen.getByText('(2 items)')).toBeInTheDocument()
  })

  it('should navigate to /checkout when checkout button is clicked', async () => {
    useCartStore.setState({
      items: [{ productId: 'p1', name: 'Widget', price: 10.0, quantity: 1 }],
      total: 10.0,
      itemCount: 1,
    })
    renderWithProviders(<CartPage />)
    await userEvent.click(screen.getByRole('button', { name: /proceed to checkout/i }))
  })
})
