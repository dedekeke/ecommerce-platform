import { describe, it, expect, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { ProductGrid } from './ProductGrid'
import { renderWithProviders } from '../../test/renderWithProviders'
import { mockProducts } from '../../test/mocks/products'

describe('ProductGrid', () => {
  describe('Rendering', () => {
    it('should render all products', () => {
      renderWithProviders(<ProductGrid products={mockProducts} />)
      const cards = screen.getAllByTestId('product-card')
      expect(cards).toHaveLength(mockProducts.length)
    })

    it('should render product grid container', () => {
      renderWithProviders(<ProductGrid products={mockProducts} />)
      expect(screen.getByTestId('product-grid')).toBeInTheDocument()
    })

    it('should render each product name', () => {
      renderWithProviders(<ProductGrid products={mockProducts} />)
      mockProducts.forEach((product) => {
        expect(screen.getByText(product.name)).toBeInTheDocument()
      })
    })
  })

  describe('Loading State', () => {
    it('should show loading skeletons when loading', () => {
      renderWithProviders(<ProductGrid products={[]} isLoading />)
      const skeletons = screen.getAllByTestId('product-card-skeleton')
      expect(skeletons.length).toBeGreaterThan(0)
    })

    it('should show specified number of skeletons', () => {
      renderWithProviders(<ProductGrid products={[]} isLoading skeletonCount={8} />)
      const skeletons = screen.getAllByTestId('product-card-skeleton')
      expect(skeletons).toHaveLength(8)
    })

    it('should not show products when loading', () => {
      renderWithProviders(<ProductGrid products={mockProducts} isLoading />)
      expect(screen.queryByTestId('product-card')).not.toBeInTheDocument()
    })
  })

  describe('Empty State', () => {
    it('should show empty message when no products', () => {
      renderWithProviders(<ProductGrid products={[]} />)
      expect(screen.getByText(/no products found/i)).toBeInTheDocument()
    })

    it('should show custom empty message', () => {
      renderWithProviders(<ProductGrid products={[]} emptyMessage="Custom empty message" />)
      expect(screen.getByText('Custom empty message')).toBeInTheDocument()
    })
  })

  describe('User Interactions', () => {
    it('should call onAddToCart when add to cart is clicked', async () => {
      const onAddToCart = vi.fn()
      renderWithProviders(<ProductGrid products={mockProducts} onAddToCart={onAddToCart} />)

      const firstCard = screen.getAllByTestId('product-card')[0]
      const addButton = within(firstCard).getByRole('button', { name: /add to cart/i })

      const { default: userEvent } = await import('@testing-library/user-event')
      const user = userEvent.setup()
      await user.click(addButton)

      expect(onAddToCart).toHaveBeenCalledTimes(1)
    })
  })

  describe('Responsive Layout', () => {
    it('should render with responsive grid classes', () => {
      renderWithProviders(<ProductGrid products={mockProducts} />)
      const grid = screen.getByTestId('product-grid')
      expect(grid).toBeInTheDocument()
    })
  })
})
