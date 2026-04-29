import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { handlers } from '../test/mocks/handlers'
import { createOrder, getOrder } from './orderService'

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

const validPayload = {
  userId: 'user-1',
  items: [{ productId: 'prod-1', quantity: 2, price: 79.99 }],
  shippingAddress: {
    fullName: 'Jane Doe',
    line1: '123 Main St',
    city: 'San Francisco',
    state: 'CA',
    postalCode: '94105',
    country: 'US',
  },
  paymentMethodId: 'mock_card_abc',
  totalAmount: 168.97,
}

describe('createOrder', () => {
  it('should return a created order on success', async () => {
    const order = await createOrder(validPayload)
    expect(order.id).toBe('order-123')
    expect(order.orderNumber).toBe('ORD-20260429-001')
    expect(order.status).toBe('PENDING')
  })

  it('should throw when the server returns a 400', async () => {
    const { http, HttpResponse } = await import('msw')
    server.use(
      http.post('http://localhost:8080/api/orders', () =>
        HttpResponse.json({ message: 'Bad request' }, { status: 400 })
      )
    )
    await expect(createOrder({ ...validPayload, userId: '' })).rejects.toThrow()
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
