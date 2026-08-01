import { describe, it, expect, vi, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import ProductDetailPage from './ProductDetailPage'
import { useProduct } from '../hooks'
import type { Product } from '../types'

vi.mock('../hooks', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../hooks')>()
  return { ...actual, useProduct: vi.fn() }
})

const mockUseProduct = vi.mocked(useProduct)

const API_BASE = 'http://localhost:8080/api'
const PRODUCT: Product = {
  id: 'prod-99',
  sku: 'SKU-99',
  name: 'Wireless Headphones',
  description: 'Great sound',
  category: null,
  price: 99.99,
  currency: 'USD',
  images: [],
  stockQuantity: 5,
  active: true,
  inStock: true,
  available: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const server = setupServer(
  http.get(`${API_BASE}/v1/reviews/product/${PRODUCT.id}/summary`, () =>
    HttpResponse.json({ averageRating: 4.0, count: 3, distribution: { '4': 3 } }),
  ),
  http.get(`${API_BASE}/v1/reviews/product/${PRODUCT.id}`, () =>
    HttpResponse.json({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0, sort: 'helpful' }),
  ),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  delete window.__getAuthUserId
})
afterAll(() => server.close())

describe('ProductDetailPage — reviews wiring', () => {
  it('should render the ratings & reviews section for the loaded product', async () => {
    mockUseProduct.mockReturnValue({
      product: PRODUCT,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    })

    renderWithProviders(<ProductDetailPage />, { initialEntries: [`/${PRODUCT.id}`] })

    expect(screen.getByTestId('product-reviews-section')).toBeInTheDocument()
    expect(await screen.findByText('4.0')).toBeInTheDocument()
    expect(screen.getByTestId('review-signin-prompt')).toBeInTheDocument()
  })

  it('should not render the reviews section while the product is loading', () => {
    mockUseProduct.mockReturnValue({
      product: null,
      isLoading: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    })

    renderWithProviders(<ProductDetailPage />, { initialEntries: [`/${PRODUCT.id}`] })

    expect(screen.queryByTestId('product-reviews-section')).not.toBeInTheDocument()
  })
})

describe('ProductDetailPage — add to cart', () => {
  afterEach(() => {
    delete window.__ecommerceToastHost
  })

  it('should call onAddToCart with the selected quantity and toast a success message', async () => {
    mockUseProduct.mockReturnValue({
      product: PRODUCT,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    })
    window.__ecommerceToastHost = true
    const listener = vi.fn()
    window.addEventListener('ecommerce:toast', listener)

    const onAddToCart = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<ProductDetailPage onAddToCart={onAddToCart} />, {
      initialEntries: [`/${PRODUCT.id}`],
    })

    await user.click(screen.getByRole('button', { name: /add to cart/i }))

    expect(onAddToCart).toHaveBeenCalledWith(PRODUCT.id, 1)
    expect(listener).toHaveBeenCalledOnce()
    const event = listener.mock.calls[0][0] as CustomEvent
    expect(event.detail).toMatchObject({ type: 'success', message: 'Added to cart' })

    window.removeEventListener('ecommerce:toast', listener)
  })

  it('should not toast when there is no onAddToCart handler wired', async () => {
    mockUseProduct.mockReturnValue({
      product: PRODUCT,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    })
    window.__ecommerceToastHost = true
    const listener = vi.fn()
    window.addEventListener('ecommerce:toast', listener)

    const user = userEvent.setup()
    renderWithProviders(<ProductDetailPage />, { initialEntries: [`/${PRODUCT.id}`] })
    await user.click(screen.getByRole('button', { name: /add to cart/i }))

    expect(listener).not.toHaveBeenCalled()
    window.removeEventListener('ecommerce:toast', listener)
  })
})
