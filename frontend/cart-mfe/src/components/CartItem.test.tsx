import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import CartItem from './CartItem'

const baseItem = {
  productId: 'p1',
  name: 'Wireless Headphones',
  price: 79.99,
  quantity: 2,
  image: 'https://via.placeholder.com/80',
}

describe('CartItem', () => {
  it('should render item name, unit price, and subtotal', () => {
    renderWithProviders(
      <CartItem item={baseItem} onUpdateQty={vi.fn()} onRemove={vi.fn()} />
    )
    expect(screen.getByText('Wireless Headphones')).toBeInTheDocument()
    expect(screen.getByText('$79.99')).toBeInTheDocument()
    expect(screen.getByText('$159.98')).toBeInTheDocument()
  })

  it('should render the product image with alt text', () => {
    renderWithProviders(
      <CartItem item={baseItem} onUpdateQty={vi.fn()} onRemove={vi.fn()} />
    )
    const img = screen.getByRole('img', { name: /Wireless Headphones/i })
    expect(img).toBeInTheDocument()
    expect(img).toHaveAttribute('src', baseItem.image)
  })

  it('should render quantity value in stepper', () => {
    renderWithProviders(
      <CartItem item={baseItem} onUpdateQty={vi.fn()} onRemove={vi.fn()} />
    )
    expect(screen.getByRole('spinbutton', { name: /quantity/i })).toHaveValue(2)
  })

  it('should call onUpdateQty with incremented value when + is clicked', async () => {
    const onUpdateQty = vi.fn()
    renderWithProviders(
      <CartItem item={baseItem} onUpdateQty={onUpdateQty} onRemove={vi.fn()} />
    )
    await userEvent.click(screen.getByRole('button', { name: /increase quantity/i }))
    expect(onUpdateQty).toHaveBeenCalledWith('p1', 3)
  })

  it('should call onUpdateQty with decremented value when - is clicked', async () => {
    const onUpdateQty = vi.fn()
    renderWithProviders(
      <CartItem item={baseItem} onUpdateQty={onUpdateQty} onRemove={vi.fn()} />
    )
    await userEvent.click(screen.getByRole('button', { name: /decrease quantity/i }))
    expect(onUpdateQty).toHaveBeenCalledWith('p1', 1)
  })

  it('should call onRemove when remove button is clicked', async () => {
    const onRemove = vi.fn()
    renderWithProviders(
      <CartItem item={baseItem} onUpdateQty={vi.fn()} onRemove={onRemove} />
    )
    await userEvent.click(screen.getByRole('button', { name: /remove Wireless Headphones/i }))
    expect(onRemove).toHaveBeenCalledWith('p1')
  })

  it('should disable decrease button when quantity is 1', () => {
    renderWithProviders(
      <CartItem
        item={{ ...baseItem, quantity: 1 }}
        onUpdateQty={vi.fn()}
        onRemove={vi.fn()}
      />
    )
    expect(screen.getByRole('button', { name: /decrease quantity/i })).toBeDisabled()
  })

  it('should render a placeholder when no image is provided', () => {
    renderWithProviders(
      <CartItem
        item={{ ...baseItem, image: undefined }}
        onUpdateQty={vi.fn()}
        onRemove={vi.fn()}
      />
    )
    expect(screen.getByRole('img', { name: /Wireless Headphones/i })).toBeInTheDocument()
  })

  it('should be keyboard navigable — remove button is focusable', () => {
    renderWithProviders(
      <CartItem item={baseItem} onUpdateQty={vi.fn()} onRemove={vi.fn()} />
    )
    const removeBtn = screen.getByRole('button', { name: /remove Wireless Headphones/i })
    removeBtn.focus()
    expect(removeBtn).toHaveFocus()
  })
})
