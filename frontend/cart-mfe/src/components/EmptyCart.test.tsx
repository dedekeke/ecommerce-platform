import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import EmptyCart from './EmptyCart'

describe('EmptyCart', () => {
  it('should render empty cart heading', () => {
    renderWithProviders(<EmptyCart />)
    expect(screen.getByRole('heading', { name: /your cart is empty/i })).toBeInTheDocument()
  })

  it('should render a "Continue shopping" link pointing to /products', () => {
    renderWithProviders(<EmptyCart />)
    const link = screen.getByRole('link', { name: /continue shopping/i })
    expect(link).toBeInTheDocument()
    expect(link).toHaveAttribute('href', '/products')
  })

  it('should render a descriptive message', () => {
    renderWithProviders(<EmptyCart />)
    expect(
      screen.getByText(/looks like you haven't added anything yet/i)
    ).toBeInTheDocument()
  })
})
