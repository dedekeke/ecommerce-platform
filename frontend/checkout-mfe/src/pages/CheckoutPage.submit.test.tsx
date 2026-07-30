import { describe, it, expect, vi, beforeAll, afterAll, afterEach } from 'vitest'
import { screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers } from '../test/mocks/handlers'
import { renderWithProviders } from '../test/renderWithProviders'
import CheckoutPage from './CheckoutPage'
import { useCheckoutStore } from '../stores/checkoutStore'
import { useCartStore } from '../stores/cartStore'

const API_BASE = 'http://localhost:8080/api'
const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// The Payment step itself is exercised in CheckoutPage.payment.test.tsx; here we only need to
// know that it was reached.
vi.mock('../components/StripeCheckout', () => ({
  default: () => <div data-testid="stripe-checkout" />,
}))

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => {
  server.resetHandlers()
  act(() => useCheckoutStore.getState().reset())
  act(() => useCartStore.getState().clearCart())
  mockNavigate.mockReset()
})
afterAll(() => server.close())

const freshOrderResponse = (overrides: Partial<Record<string, unknown>> = {}) => ({
  orderId: 'order-123',
  orderNumber: 'ORD-001',
  status: 'PENDING',
  currency: 'USD',
  subtotal: 79.99,
  tax: 8,
  shippingCost: 5.99,
  discountAmount: null,
  loyaltyDiscount: null,
  total: 93.98,
  paymentIntentId: 'pi_1',
  clientSecret: 'secret_1',
  items: [],
  ...overrides,
})

function renderAtReviewStep() {
  act(() => {
    useCartStore.getState().addItem({ productId: 'p1', name: 'Headphones', price: 79.99 })
    useCheckoutStore.getState().setStep(1)
    useCheckoutStore.getState().setAddress({
      fullName: 'Jane Doe',
      line1: '123 Main St',
      city: 'San Francisco',
      state: 'CA',
      postalCode: '94105',
      country: 'US',
    })
  })
  return renderWithProviders(<CheckoutPage />)
}

describe('CheckoutPage — order submission (order-first checkout, PR#122)', () => {
  it('should call POST /api/orders and advance to the Payment step on success', async () => {
    renderAtReviewStep()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /continue to payment/i })).toBeInTheDocument()
    })
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(() => {
      expect(screen.getByTestId('stripe-checkout')).toBeInTheDocument()
    })
  })

  it('should show an "already being submitted" message and stay on Review on a 409 (do not retry)', async () => {
    let callCount = 0
    server.use(
      http.post(`${API_BASE}/orders`, () => {
        callCount += 1
        return HttpResponse.json({ message: 'Checkout is already being processed' }, { status: 409 })
      })
    )
    renderAtReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    await waitFor(() => expect(screen.getByText(/already being submitted/i)).toBeInTheDocument())
    expect(screen.queryByTestId('stripe-checkout')).not.toBeInTheDocument()
    // A 409 must never be retried — only the one deliberate request should have been sent.
    expect(callCount).toBe(1)
  })

  it('should show a generic error and stay on Review on a 400 (validation failure)', async () => {
    server.use(
      http.post(`${API_BASE}/orders`, () =>
        HttpResponse.json({ message: 'Shipping address is required' }, { status: 400 })
      )
    )
    renderAtReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(() => expect(screen.getByText(/failed to place order/i)).toBeInTheDocument())
    expect(screen.queryByTestId('stripe-checkout')).not.toBeInTheDocument()
  })

  it('should retry automatically on a transient 502 with the same Idempotency-Key, then advance to Payment', async () => {
    const seenKeys: Array<string | null> = []
    let callCount = 0
    server.use(
      http.post(`${API_BASE}/orders`, async ({ request }) => {
        callCount += 1
        seenKeys.push(request.headers.get('Idempotency-Key'))
        if (callCount === 1) {
          return HttpResponse.json({ message: 'saga/downstream failure' }, { status: 502 })
        }
        return HttpResponse.json(freshOrderResponse(), { status: 201 })
      })
    )
    renderAtReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    // Single click — axios-retry replays the 502 transparently inside the same request.
    await waitFor(
      () => expect(screen.getByTestId('stripe-checkout')).toBeInTheDocument(),
      { timeout: 10000 }
    )

    expect(callCount).toBe(2)
    expect(seenKeys[0]).toBeTruthy()
    expect(seenKeys[0]).toBe(seenKeys[1])
  }, 15000)

  it('should show a generic error when 502s persist past all retries', async () => {
    server.use(
      http.post(`${API_BASE}/orders`, () =>
        HttpResponse.json({ message: 'saga/downstream failure' }, { status: 502 })
      )
    )
    renderAtReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(
      () => expect(screen.getByText(/failed to place order/i)).toBeInTheDocument(),
      { timeout: 10000 }
    )
  }, 15000)

  it('should surface an error when a fresh order is created with no clientSecret at all', async () => {
    server.use(
      http.post(`${API_BASE}/orders`, () =>
        HttpResponse.json(freshOrderResponse({ clientSecret: null }), { status: 201 })
      )
    )
    renderAtReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(() =>
      expect(screen.getByText(/payment could not be started/i)).toBeInTheDocument()
    )
    expect(screen.queryByTestId('stripe-checkout')).not.toBeInTheDocument()
  })

  it('should reuse the same Idempotency-Key on a manual retry after a failed submit', async () => {
    const seenKeys: Array<string | null> = []
    let failNext = true
    server.use(
      http.post(`${API_BASE}/orders`, async ({ request }) => {
        seenKeys.push(request.headers.get('Idempotency-Key'))
        if (failNext) {
          failNext = false
          return HttpResponse.json({ message: 'validation error' }, { status: 400 })
        }
        return HttpResponse.json(freshOrderResponse(), { status: 201 })
      })
    )
    renderAtReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(() => expect(screen.getByText(/failed to place order/i)).toBeInTheDocument())

    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(() => expect(screen.getByTestId('stripe-checkout')).toBeInTheDocument())

    expect(seenKeys).toHaveLength(2)
    expect(seenKeys[0]).toBeTruthy()
    expect(seenKeys[0]).toBe(seenKeys[1])
  })

  it('should include promotionCode in the order payload when a promotion is applied', async () => {
    let capturedBody: Record<string, unknown> | undefined
    server.use(
      http.post(`${API_BASE}/orders`, async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(freshOrderResponse(), { status: 201 })
      })
    )
    renderAtReviewStep()
    act(() => {
      useCartStore.setState({ promotionCode: 'SAVE10', discountAmount: 5, promotionName: '10% off' })
    })
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    await waitFor(() => expect(screen.getByTestId('stripe-checkout')).toBeInTheDocument())
    expect(capturedBody?.promotionCode).toBe('SAVE10')
  })

  it('should omit promotionCode from the order payload when no promotion is applied', async () => {
    let capturedBody: Record<string, unknown> | undefined
    server.use(
      http.post(`${API_BASE}/orders`, async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(freshOrderResponse(), { status: 201 })
      })
    )
    renderAtReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))

    await waitFor(() => expect(screen.getByTestId('stripe-checkout')).toBeInTheDocument())
    expect(capturedBody?.promotionCode).toBeUndefined()
  })

  it('should disable the Continue-to-payment button while submitting', async () => {
    let resolve!: () => void
    const slow = new Promise<void>((res) => {
      resolve = res
    })
    server.use(
      http.post(`${API_BASE}/orders`, async () => {
        await slow
        return HttpResponse.json(freshOrderResponse(), { status: 201 })
      })
    )
    renderAtReviewStep()
    await userEvent.click(screen.getByRole('button', { name: /continue to payment/i }))
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /placing order/i })).toBeDisabled()
    })
    resolve()
  })
})
