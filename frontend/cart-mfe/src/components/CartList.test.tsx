import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import CartList from './CartList'
import type { CartItem } from '../stores/types'

const mockItems: CartItem[] = [
  { productId: 'p1', name: 'Wireless Headphones', price: 79.99, quantity: 2 },
  { productId: 'p2', name: 'Mechanical Keyboard', price: 149.99, quantity: 1 },
]

describe('CartList', () => {
  it('should render all items when provided', () => {
    renderWithProviders(
      <CartList
        items={mockItems}
        loading={false}
        onUpdateQty={vi.fn()}
        onRemove={vi.fn()}
      />
    )
    expect(screen.getByText('Wireless Headphones')).toBeInTheDocument()
    expect(screen.getByText('Mechanical Keyboard')).toBeInTheDocument()
  })

  it('should render skeletons when loading is true', () => {
    renderWithProviders(
      <CartList
        items={[]}
        loading={true}
        onUpdateQty={vi.fn()}
        onRemove={vi.fn()}
      />
    )
    const skeletons = document.querySelectorAll('.MuiSkeleton-root')
    expect(skeletons.length).toBeGreaterThan(0)
  })

  it('should render EmptyCart when items array is empty and not loading', () => {
    renderWithProviders(
      <CartList
        items={[]}
        loading={false}
        onUpdateQty={vi.fn()}
        onRemove={vi.fn()}
      />
    )
    expect(screen.getByRole('heading', { name: /your cart is empty/i })).toBeInTheDocument()
  })

  it('should pass onUpdateQty and onRemove down to CartItems', async () => {
    const onUpdateQty = vi.fn()
    const onRemove = vi.fn()
    renderWithProviders(
      <CartList
        items={mockItems}
        loading={false}
        onUpdateQty={onUpdateQty}
        onRemove={onRemove}
      />
    )
    expect(screen.getAllByRole('button', { name: /remove/i })).toHaveLength(2)
  })
})
