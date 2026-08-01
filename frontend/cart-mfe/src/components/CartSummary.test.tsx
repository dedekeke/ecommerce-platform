import { describe, it, expect, vi, beforeEach, beforeAll, afterEach, afterAll } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers } from '../test/mocks'
import { mockInvalidDiscount } from '../test/mocks/promotion'
import { renderWithProviders } from '../test/renderWithProviders'
import { useCartStore } from '../stores/cartStore'
import CartSummary from './CartSummary'

const API_BASE = 'http://localhost:8080/api'
const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('CartSummary', () => {
  beforeEach(() => {
    useCartStore.setState({
      items: [],
      total: 0,
      itemCount: 0,
      promotionCode: null,
      discountAmount: null,
      promotionName: null,
    })
  })

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

  it('should expose the grand total via the cart-total testid for e2e assertions', () => {
    renderWithProviders(<CartSummary subtotal={40} onCheckout={vi.fn()} />)
    expect(screen.getByTestId('cart-total')).toHaveTextContent('$49.00')
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

  it('should render the promo code input', () => {
    renderWithProviders(<CartSummary subtotal={100} onCheckout={vi.fn()} />)
    expect(screen.getByRole('textbox', { name: /promo code/i })).toBeInTheDocument()
  })

  describe('with an applied promotion', () => {
    beforeEach(() => {
      useCartStore
        .getState()
        .applyPromotion({ code: 'SAVE10', discountAmount: 10, promotionName: '10 Off Sale' })
    })

    it('should show the discount line, ordered after subtotal and before tax', () => {
      renderWithProviders(<CartSummary subtotal={100} onCheckout={vi.fn()} />)
      const labels = screen
        .getAllByText(/subtotal|discount|est\. tax|shipping/i)
        .map((el) => el.textContent)
      expect(labels[0]).toMatch(/subtotal/i)
      expect(labels[1]).toMatch(/discount/i)
      expect(labels[2]).toMatch(/est\. tax/i)
      expect(labels[3]).toMatch(/shipping/i)
      expect(screen.getByTestId('cart-discount')).toHaveTextContent('-$10.00')
    })

    it('should compute tax, shipping and total off the discounted subtotal', () => {
      // subtotal 100 - discount 10 = 90 -> tax 9.00, free shipping (>= 50), total 99.00
      renderWithProviders(<CartSummary subtotal={100} onCheckout={vi.fn()} />)
      expect(screen.getByText('$9.00')).toBeInTheDocument()
      expect(screen.getByTestId('cart-total')).toHaveTextContent('$99.00')
    })

    it('should render the applied code and allow removing it', async () => {
      renderWithProviders(<CartSummary subtotal={100} onCheckout={vi.fn()} />)
      expect(screen.getByText(/"SAVE10" applied/)).toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: /remove/i }))

      expect(screen.queryByTestId('cart-discount')).not.toBeInTheDocument()
      expect(useCartStore.getState().promotionCode).toBeNull()
    })

    it('should show an inline notice and drop the discount when the subtotal falls below the promo minimum', async () => {
      server.use(
        http.post(`${API_BASE}/promotions/validate`, () => HttpResponse.json(mockInvalidDiscount)),
      )
      const { rerender } = renderWithProviders(<CartSummary subtotal={100} onCheckout={vi.fn()} />)
      expect(screen.getByTestId('cart-discount')).toBeInTheDocument()

      rerender(<CartSummary subtotal={5} onCheckout={vi.fn()} />)

      await waitFor(
        () => expect(screen.getByTestId('promo-revalidation-notice')).toHaveTextContent(/no longer applies/i),
        { timeout: 2000 },
      )
      expect(screen.queryByTestId('cart-discount')).not.toBeInTheDocument()
    })
  })
})
