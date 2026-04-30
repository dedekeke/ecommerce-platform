import { describe, it, expect, vi, beforeAll, afterAll, afterEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { handlers } from '../test/mocks/handlers'
import { renderWithProviders } from '../test/renderWithProviders'
import CheckoutPage from './CheckoutPage'
import { useCheckoutStore } from '../stores/checkoutStore'
import { useCartStore } from '../stores/cartStore'

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => {
  server.resetHandlers()
  act(() => useCheckoutStore.getState().reset())
  act(() => useCartStore.getState().clearCart())
  mockNavigate.mockReset()
})
afterAll(() => server.close())

function renderAtReviewStep() {
  act(() => {
    useCartStore.getState().addItem({ productId: 'p1', name: 'Headphones', price: 79.99 })
    useCheckoutStore.getState().setStep(2)
    useCheckoutStore.getState().setAddress({
      fullName: 'Jane Doe',
      line1: '123 Main St',
      city: 'San Francisco',
      state: 'CA',
      postalCode: '94105',
      country: 'US',
    })
    useCheckoutStore.getState().setPaymentMethod('mock_card_abc')
  })
  return renderWithProviders(<CheckoutPage />)
}

describe('CheckoutPage — order submission', () => {

  it('should call POST /api/orders and navigate to confirmation on success', async () => {
    renderAtReviewStep()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /place order/i })).toBeInTheDocument()
    })
    await userEvent.click(screen.getByRole('button', { name: /place order/i }))
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('confirmation/order-123')
    })
  })

  it('should show an error alert when the order API call fails', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.post('http://localhost:8080/api/orders', () =>
        HttpResponse.json({ message: 'Server error' }, { status: 500 })
      )
    )
    renderAtReviewStep()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /place order/i })).toBeInTheDocument()
    })
    await userEvent.click(screen.getByRole('button', { name: /place order/i }))
    // axios-retry replays 5xx three times with exponential backoff before
    // the error reaches the UI, so allow extra time for the alert to appear.
    await waitFor(
      () => {
        expect(screen.getByText(/failed to place order/i)).toBeInTheDocument()
      },
      { timeout: 10000 }
    )
  }, 15000)

  it('should disable Place Order button while submitting', async () => {
    const { http, HttpResponse } = await import('msw')
    let resolve!: () => void
    const slow = new Promise<void>((res) => { resolve = res })
    server.use(
      http.post('http://localhost:8080/api/orders', async () => {
        await slow
        return HttpResponse.json(
          { id: 'order-123', orderNumber: 'ORD-001', status: 'PENDING', items: [], shippingAddress: {}, totalAmount: 0 },
          { status: 201 }
        )
      })
    )
    renderAtReviewStep()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /place order/i })).toBeInTheDocument()
    })
    await userEvent.click(screen.getByRole('button', { name: /place order/i }))
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /placing order/i })).toBeDisabled()
    })
    resolve()
  })
})
