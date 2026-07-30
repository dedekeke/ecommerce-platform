import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers } from '../test/mocks/handlers'
import { createOrder, createGuestOrder, getOrder } from './orderService'
import type { CheckoutRequestPayload, GuestCheckoutRequestPayload } from './types'

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

const validPayload: CheckoutRequestPayload = {
  userId: 'user-1',
  shippingAddress: {
    street: '123 Main St',
    city: 'San Francisco',
    state: 'CA',
    postalCode: '94105',
    country: 'US',
  },
}

describe('createOrder', () => {
  it('should return the checkout response on a fresh (201) order', async () => {
    const { response, isReplay } = await createOrder(validPayload, 'idem-key-1')
    expect(response.orderId).toBe('order-123')
    expect(response.orderNumber).toBe('ORD-20260429-001')
    expect(response.clientSecret).toBe('pi_test_123_secret_abc')
    expect(isReplay).toBe(false)
  })

  it('should send the Idempotency-Key header on the request', async () => {
    const { http, HttpResponse } = await import('msw')
    let seenHeader: string | null = null
    server.use(
      http.post('http://localhost:8080/api/orders', async ({ request }) => {
        seenHeader = request.headers.get('Idempotency-Key')
        return HttpResponse.json(
          { orderId: 'order-123', orderNumber: 'ORD-1', status: 'PENDING', currency: 'USD', subtotal: 0, tax: 0, shippingCost: 0, discountAmount: null, loyaltyDiscount: null, total: 0, paymentIntentId: 'pi_1', clientSecret: 'secret_1', items: [] },
          { status: 201 }
        )
      })
    )
    await createOrder(validPayload, 'idem-key-abc')
    expect(seenHeader).toBe('idem-key-abc')
  })

  it('should mark the outcome as a replay on a 200 response', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.post('http://localhost:8080/api/orders', () =>
        HttpResponse.json(
          { orderId: 'order-123', orderNumber: 'ORD-1', status: 'PENDING', currency: 'USD', subtotal: 0, tax: 0, shippingCost: 0, discountAmount: null, loyaltyDiscount: null, total: 0, paymentIntentId: 'pi_1', clientSecret: null, items: [] },
          { status: 200 }
        )
      )
    )
    const { response, isReplay } = await createOrder(validPayload, 'idem-key-1')
    expect(isReplay).toBe(true)
    expect(response.clientSecret).toBeNull()
  })

  it('should throw when the server returns a 400', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.post('http://localhost:8080/api/orders', () =>
        HttpResponse.json({ message: 'Bad request' }, { status: 400 })
      )
    )
    await expect(createOrder(validPayload, 'idem-key-1')).rejects.toThrow()
  })

  it('should throw with a 409 status when a checkout with this key is already in flight', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.post('http://localhost:8080/api/orders', () =>
        HttpResponse.json({ message: 'Checkout is already being processed' }, { status: 409 })
      )
    )
    await expect(createOrder(validPayload, 'idem-key-1')).rejects.toMatchObject({
      response: { status: 409 },
    })
  })
})

const guestPayload: GuestCheckoutRequestPayload = {
  email: 'guest@example.com',
  shippingAddress: {
    street: '123 Main St',
    city: 'San Francisco',
    state: 'CA',
    postalCode: '94105',
    country: 'US',
  },
}

describe('createGuestOrder', () => {
  it('should return the checkout response on a fresh (201) guest order', async () => {
    const { response, isReplay } = await createGuestOrder(guestPayload, 'guest-idem-1')
    expect(response.orderId).toBe('guest-order-123')
    expect(response.clientSecret).toBe('pi_guest_123_secret_abc')
    expect(isReplay).toBe(false)
  })

  it('should POST to /orders/guest with the email and Idempotency-Key', async () => {
    const { http, HttpResponse } = await import('msw')
    let seenKey: string | null = null
    let seenBody: GuestCheckoutRequestPayload | null = null
    server.use(
      http.post('http://localhost:8080/api/orders/guest', async ({ request }) => {
        seenKey = request.headers.get('Idempotency-Key')
        seenBody = (await request.json()) as GuestCheckoutRequestPayload
        return HttpResponse.json(
          { orderId: 'guest-order-123', orderNumber: 'ORD-1', status: 'PENDING', currency: 'USD', subtotal: 0, tax: 0, shippingCost: 0, discountAmount: null, loyaltyDiscount: null, total: 0, paymentIntentId: 'pi_1', clientSecret: 'secret_1', items: [] },
          { status: 201 }
        )
      })
    )
    await createGuestOrder(guestPayload, 'guest-idem-abc')
    expect(seenKey).toBe('guest-idem-abc')
    expect(seenBody!.email).toBe('guest@example.com')
    // A guest request must never carry a userId — identity is server-derived.
    expect((seenBody as unknown as Record<string, unknown>).userId).toBeUndefined()
  })

  it('should mark the outcome as a replay on a 200 response', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.post('http://localhost:8080/api/orders/guest', () =>
        HttpResponse.json(
          { orderId: 'guest-order-123', orderNumber: 'ORD-1', status: 'PENDING', currency: 'USD', subtotal: 0, tax: 0, shippingCost: 0, discountAmount: null, loyaltyDiscount: null, total: 0, paymentIntentId: 'pi_1', clientSecret: null, items: [] },
          { status: 200 }
        )
      )
    )
    const { isReplay } = await createGuestOrder(guestPayload, 'guest-idem-1')
    expect(isReplay).toBe(true)
  })
})

describe('getOrder', () => {
  it('should return an order by id, using the OrderResponse field names', async () => {
    const order = await getOrder('order-123')
    expect(order.orderId).toBe('order-123')
    expect(order.orderNumber).toBe('ORD-20260429-001')
    expect(order.total).toBe(168.97)
    expect(order.shippingAddress?.street).toBe('123 Main St')
    expect(order.items[0]).toMatchObject({ productId: 'prod-1', productName: 'Headphones', subtotal: 159.98 })
  })

  it('should throw on a 404 response', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.get('http://localhost:8080/api/orders/:orderId', () =>
        HttpResponse.json({ message: 'Not found' }, { status: 404 })
      )
    )
    await expect(getOrder('does-not-exist')).rejects.toThrow()
  })
})

describe('error toast opt-out (callers render their own inline error UI)', () => {
  function captureToasts() {
    const events: CustomEvent[] = []
    const listener = (e: Event) => events.push(e as CustomEvent)
    window.addEventListener('ecommerce:toast', listener)
    return { events, cleanup: () => window.removeEventListener('ecommerce:toast', listener) }
  }

  beforeAll(() => {
    window.__ecommerceToastHost = true
  })
  afterAll(() => {
    delete window.__ecommerceToastHost
  })

  it('should NOT toast when createOrder fails (CheckoutPage renders submitError inline)', async () => {
    const { http, HttpResponse } = await import('msw')
    const { events, cleanup } = captureToasts()
    server.use(
      http.post('http://localhost:8080/api/orders', () =>
        HttpResponse.json({ message: 'boom' }, { status: 400 })
      )
    )
    await expect(createOrder(validPayload, 'idem-key-1')).rejects.toThrow()
    expect(events).toHaveLength(0)
    cleanup()
  })

  it('should NOT toast when createGuestOrder fails (CheckoutPage renders submitError inline)', async () => {
    const { http, HttpResponse } = await import('msw')
    const { events, cleanup } = captureToasts()
    server.use(
      http.post('http://localhost:8080/api/orders/guest', () =>
        HttpResponse.json({ message: 'boom' }, { status: 400 })
      )
    )
    await expect(createGuestOrder(guestPayload, 'guest-idem-1')).rejects.toThrow()
    expect(events).toHaveLength(0)
    cleanup()
  })

  it('should NOT toast when getOrder fails (ConfirmationPage renders its own error Alert)', async () => {
    const { http, HttpResponse } = await import('msw')
    const { events, cleanup } = captureToasts()
    server.use(
      http.get('http://localhost:8080/api/orders/:orderId', () =>
        HttpResponse.json({ message: 'Not found' }, { status: 404 })
      )
    )
    await expect(getOrder('does-not-exist')).rejects.toThrow()
    expect(events).toHaveLength(0)
    cleanup()
  })
})
