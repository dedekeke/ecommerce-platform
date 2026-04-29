import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import CartSummary from './CartSummary'

describe('CartSummary', () => {
  it('should render subtotal correctly', () => {
    renderWithProviders(<CartSummary subtotal={100} onCheckout={vi.fn()} />)
    expect(screen.getByText('$100.00')).toBeInTheDocument()
  })

  it('should display 10% estimated tax', () => {
    renderWithProviders(<CartSummary subtotal={100} onCheckout={vi.fn()} />)
    expect(screen.getByText(/est\. tax/i)).toBeInTheDocument()
    expect(screen.getByText('$10.00')).toBeInTheDocument()
  })

  it('should display $5.00 shipping when subtotal is below $50', () => {
    renderWithProviders(<CartSummary subtotal={30} onCheckout={vi.fn()} />)
    expect(screen.getByText('$5.00')).toBeInTheDocument()
  })

  it('should display free shipping when subtotal is $50 or more', () => {
    renderWithProviders(<CartSummary subtotal={50} onCheckout={vi.fn()} />)
    expect(screen.getByText(/free/i)).toBeInTheDocument()
  })

  it('should calculate total as subtotal + tax + shipping for orders under $50', () => {
    renderWithProviders(<CartSummary subtotal={40} onCheckout={vi.fn()} />)
    // 40 + 4 (tax 10%) + 5 (shipping) = 49
    expect(screen.getByText('$49.00')).toBeInTheDocument()
  })

  it('should calculate total as subtotal + tax with free shipping for orders $50+', () => {
    renderWithProviders(<CartSummary subtotal={100} onCheckout={vi.fn()} />)
    // 100 + 10 (tax) + 0 (free shipping) = 110
    expect(screen.getByText('$110.00')).toBeInTheDocument()
  })

  it('should render "Proceed to checkout" button', () => {
    renderWithProviders(<CartSummary subtotal={100} onCheckout={vi.fn()} />)
    expect(
      screen.getByRole('button', { name: /proceed to checkout/i })
    ).toBeInTheDocument()
  })

  it('should call onCheckout when checkout button is clicked', async () => {
    const onCheckout = vi.fn()
    renderWithProviders(<CartSummary subtotal={100} onCheckout={onCheckout} />)
    await userEvent.click(screen.getByRole('button', { name: /proceed to checkout/i }))
    expect(onCheckout).toHaveBeenCalledOnce()
  })

  it('should disable checkout button when subtotal is 0', () => {
    renderWithProviders(<CartSummary subtotal={0} onCheckout={vi.fn()} />)
    expect(screen.getByRole('button', { name: /proceed to checkout/i })).toBeDisabled()
  })

  it('should display a shipping threshold message when subtotal is below $50', () => {
    renderWithProviders(<CartSummary subtotal={30} onCheckout={vi.fn()} />)
    expect(screen.getByText(/add \$20\.00 more for free shipping/i)).toBeInTheDocument()
  })
})
