import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers } from '../test/mocks/handlers'
import { createPaymentIntent } from './paymentService'

const server = setupServer(...handlers)
const API_BASE = 'http://localhost:8080/api'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

const validPayload = {
  orderId: 'order-1',
  userId: 'user-1',
  amount: 42.0,
  currency: 'USD',
}

describe('createPaymentIntent', () => {
  it('should return a client secret on success', async () => {
    const res = await createPaymentIntent(validPayload)
    expect(res.clientSecret).toBe('pi_test_123_secret_abc')
    expect(res.paymentIntentId).toBe('pi_test_123')
    expect(res.status).toBe('PENDING')
  })

  it('should reject when the backend returns an error', async () => {
    server.use(
      http.post(`${API_BASE}/payments/intents`, () =>
        HttpResponse.json({ message: 'gateway down' }, { status: 500 })
      )
    )
    await expect(createPaymentIntent(validPayload)).rejects.toBeDefined()
  })
})
