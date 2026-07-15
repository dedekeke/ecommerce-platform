import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers } from '../test/mocks/handlers'
import { createOrder, getOrder } from './orderService'
import type { CheckoutRequestPayload } from './types'

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

describe('getOrder', () => {
  it('should return an order by id', async () => {
    const order = await getOrder('order-123')
    expect(order.id).toBe('order-123')
    expect(order.orderNumber).toBe('ORD-20260429-001')
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
