import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ProductCard } from './ProductCard'
import { renderWithProviders } from '../../test/renderWithProviders'
import { mockProduct, createMockProduct } from '../../test/mocks/products'

describe('ProductCard', () => {
  describe('Rendering', () => {
    it('should render product name', () => {
      renderWithProviders(<ProductCard product={mockProduct} />)
      expect(screen.getByText(mockProduct.name)).toBeInTheDocument()
    })

    it('should render formatted price', () => {
      renderWithProviders(<ProductCard product={mockProduct} />)
      expect(screen.getByText('$99.99')).toBeInTheDocument()
    })

    it('should render product image', () => {
      renderWithProviders(<ProductCard product={mockProduct} />)
      const image = screen.getByRole('img', { name: mockProduct.name })
      expect(image).toBeInTheDocument()
      expect(image).toHaveAttribute('src', mockProduct.images[0])
    })

    it('should render category name', () => {
      renderWithProviders(<ProductCard product={mockProduct} />)
      expect(screen.getByText(mockProduct.category!.name)).toBeInTheDocument()
    })

    it('should have data-testid for card element', () => {
      renderWithProviders(<ProductCard product={mockProduct} />)
      expect(screen.getByTestId('product-card')).toBeInTheDocument()
    })
  })

  describe('Stock Status', () => {
    it('should show "In Stock" badge when product is in stock', () => {
      const inStockProduct = createMockProduct({ inStock: true, stockQuantity: 10 })
      renderWithProviders(<ProductCard product={inStockProduct} />)
      expect(screen.getByText(/in stock/i)).toBeInTheDocument()
    })

    it('should show "Out of Stock" badge when product is not in stock', () => {
      const outOfStockProduct = createMockProduct({ inStock: false, stockQuantity: 0 })
      renderWithProviders(<ProductCard product={outOfStockProduct} />)
      expect(screen.getByText(/out of stock/i)).toBeInTheDocument()
    })

    it('should disable add to cart button when out of stock', () => {
      const outOfStockProduct = createMockProduct({ inStock: false, stockQuantity: 0 })
      renderWithProviders(<ProductCard product={outOfStockProduct} />)
      const addButton = screen.getByRole('button', { name: /add to cart/i })
      expect(addButton).toBeDisabled()
    })
  })

  describe('Price Formatting', () => {
    it('should format price with USD currency symbol', () => {
      const product = createMockProduct({ price: 1234.56, currency: 'USD' })
      renderWithProviders(<ProductCard product={product} />)
      expect(screen.getByText('$1,234.56')).toBeInTheDocument()
    })

    it('should format price with EUR currency symbol', () => {
      const product = createMockProduct({ price: 99.99, currency: 'EUR' })
      renderWithProviders(<ProductCard product={product} currency="EUR" />)
      expect(screen.getByText(/€99.99/)).toBeInTheDocument()
    })
  })

  describe('User Interactions', () => {
    it('should call onAddToCart when add to cart button is clicked', async () => {
      const user = userEvent.setup()
      const onAddToCart = vi.fn()
      renderWithProviders(<ProductCard product={mockProduct} onAddToCart={onAddToCart} />)

      const addButton = screen.getByRole('button', { name: /add to cart/i })
      await user.click(addButton)

      expect(onAddToCart).toHaveBeenCalledTimes(1)
      expect(onAddToCart).toHaveBeenCalledWith(mockProduct.id, 1)
    })

    it('should toast "Added to cart" when add to cart succeeds', async () => {
      window.__ecommerceToastHost = true
      const listener = vi.fn()
      window.addEventListener('ecommerce:toast', listener)

      const user = userEvent.setup()
      renderWithProviders(<ProductCard product={mockProduct} onAddToCart={vi.fn()} />)
      await user.click(screen.getByRole('button', { name: /add to cart/i }))

      expect(listener).toHaveBeenCalledOnce()
      const event = listener.mock.calls[0][0] as CustomEvent
      expect(event.detail).toMatchObject({ type: 'success', message: 'Added to cart' })

      window.removeEventListener('ecommerce:toast', listener)
      delete window.__ecommerceToastHost
    })

    it('should not toast when the out-of-stock button is clicked (no-op)', async () => {
      window.__ecommerceToastHost = true
      const listener = vi.fn()
      window.addEventListener('ecommerce:toast', listener)

      const outOfStockProduct = createMockProduct({ inStock: false })
      renderWithProviders(<ProductCard product={outOfStockProduct} onAddToCart={vi.fn()} />)

      expect(listener).not.toHaveBeenCalled()

      window.removeEventListener('ecommerce:toast', listener)
      delete window.__ecommerceToastHost
    })

    it('should not allow clicking add to cart when button is disabled', () => {
      const onAddToCart = vi.fn()
      const outOfStockProduct = createMockProduct({ inStock: false })
      renderWithProviders(<ProductCard product={outOfStockProduct} onAddToCart={onAddToCart} />)

      const addButton = screen.getByRole('button', { name: /add to cart/i })
      expect(addButton).toBeDisabled()
      expect(addButton).toHaveStyle({ pointerEvents: 'none' })
    })

    it('should navigate to product detail when card is clicked', () => {
      renderWithProviders(<ProductCard product={mockProduct} />, {
        initialEntries: ['/products'],
      })

      const cardLink = screen.getByRole('link')
      expect(cardLink).toHaveAttribute('href', `/products/${mockProduct.id}`)
    })
  })

  describe('Accessibility', () => {
    it('should have accessible image with alt text', () => {
      renderWithProviders(<ProductCard product={mockProduct} />)
      const image = screen.getByRole('img')
      expect(image).toHaveAttribute('alt', mockProduct.name)
    })

    it('should have accessible add to cart button', () => {
      renderWithProviders(<ProductCard product={mockProduct} />)
      const button = screen.getByRole('button', { name: /add to cart/i })
      expect(button).toBeInTheDocument()
    })
  })

  describe('Fallback Image', () => {
    it('should render placeholder when product has no images', () => {
      const productNoImages = createMockProduct({ images: [] })
      renderWithProviders(<ProductCard product={productNoImages} />)
      expect(screen.getByTestId('product-image-placeholder')).toBeInTheDocument()
    })
  })
})
