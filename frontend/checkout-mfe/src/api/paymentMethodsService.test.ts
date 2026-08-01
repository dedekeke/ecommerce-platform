import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { listSavedMethods, confirmSavedMethodPayment } from './paymentMethodsService'
import type { SavedPaymentMethod } from './types'

const API_BASE = 'http://localhost:8080/api'

const savedMethod: SavedPaymentMethod = {
  id: 1,
  userId: 'auth0|test-user',
  provider: 'stripe',
  providerId: 'pm_test_visa',
  last4: '4242',
  brand: 'visa',
  expMonth: 12,
  expYear: 2027,
  isDefault: true,
  createdAt: '2026-01-01T00:00:00.000Z',
}

const server = setupServer(
  http.get(`${API_BASE}/payments/methods/user/:userId`, () => HttpResponse.json([savedMethod]))
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('listSavedMethods', () => {
  it('should return the saved payment methods for the given user', async () => {
    const methods = await listSavedMethods('auth0|test-user')
    expect(methods).toEqual([savedMethod])
  })

  it('should request the correct user-scoped endpoint', async () => {
    let seenUrl: string | null = null
    server.use(
      http.get(`${API_BASE}/payments/methods/user/:userId`, ({ request, params }) => {
        seenUrl = request.url
        return HttpResponse.json(params.userId === 'user-42' ? [savedMethod] : [])
      })
    )
    await listSavedMethods('user-42')
    expect(seenUrl).toContain('/payments/methods/user/user-42')
  })

  it('should return an empty array when the user has no saved methods', async () => {
    server.use(http.get(`${API_BASE}/payments/methods/user/:userId`, () => HttpResponse.json([])))
    const methods = await listSavedMethods('auth0|test-user')
    expect(methods).toEqual([])
  })

  it('should throw when the server returns an error', async () => {
    server.use(
      http.get(`${API_BASE}/payments/methods/user/:userId`, () =>
        HttpResponse.json({ message: 'Internal error' }, { status: 500 })
      )
    )
    await expect(listSavedMethods('auth0|test-user')).rejects.toThrow()
  })
})

describe('confirmSavedMethodPayment', () => {
  it('should POST the paymentIntentId and paymentMethodId to the confirm-saved endpoint', async () => {
    let seenUrl: string | null = null
    let seenBody: unknown = null
    server.use(
      http.post(`${API_BASE}/payments/intents/confirm-saved`, async ({ request }) => {
        seenUrl = request.url
        seenBody = await request.json()
        return HttpResponse.json({
          paymentId: 1,
          paymentIntentId: 'pi_test_123',
          clientSecret: 'pi_test_123_secret_abc',
          status: 'COMPLETED',
        })
      })
    )

    const result = await confirmSavedMethodPayment('pi_test_123', 'pm_test_visa')

    expect(seenUrl).toBe(`${API_BASE}/payments/intents/confirm-saved`)
    expect(seenBody).toEqual({ paymentIntentId: 'pi_test_123', paymentMethodId: 'pm_test_visa' })
    expect(result).toEqual({
      paymentId: 1,
      paymentIntentId: 'pi_test_123',
      clientSecret: 'pi_test_123_secret_abc',
      status: 'COMPLETED',
    })
  })

  it('should return a non-COMPLETED status as-is without throwing', async () => {
    server.use(
      http.post(`${API_BASE}/payments/intents/confirm-saved`, () =>
        HttpResponse.json({
          paymentId: 2,
          paymentIntentId: 'pi_test_456',
          clientSecret: 'pi_test_456_secret_def',
          status: 'FAILED',
        })
      )
    )

    const result = await confirmSavedMethodPayment('pi_test_456', 'pm_test_visa')
    expect(result.status).toBe('FAILED')
  })

  it('should propagate a 403 rejection when the payment method does not belong to the caller', async () => {
    server.use(
      http.post(`${API_BASE}/payments/intents/confirm-saved`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 })
      )
    )

    await expect(confirmSavedMethodPayment('pi_test_123', 'pm_not_mine')).rejects.toMatchObject({
      response: { status: 403 },
    })
  })

  it('should propagate a 404 rejection when the payment intent is unknown', async () => {
    server.use(
      http.post(`${API_BASE}/payments/intents/confirm-saved`, () =>
        HttpResponse.json({ message: 'Not found' }, { status: 404 })
      )
    )

    await expect(confirmSavedMethodPayment('pi_unknown', 'pm_test_visa')).rejects.toMatchObject({
      response: { status: 404 },
    })
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

  it('should NOT toast when listSavedMethods fails (SavedMethodPicker renders its own error Alert)', async () => {
    const { events, cleanup } = captureToasts()
    server.use(
      http.get(`${API_BASE}/payments/methods/user/:userId`, () =>
        HttpResponse.json({ message: 'Internal error' }, { status: 500 })
      )
    )
    await expect(listSavedMethods('auth0|test-user')).rejects.toThrow()
    expect(events).toHaveLength(0)
    cleanup()
  })

  it('should NOT toast when confirmSavedMethodPayment fails (SavedMethodConfirmButton renders its own error Alert)', async () => {
    const { events, cleanup } = captureToasts()
    server.use(
      http.post(`${API_BASE}/payments/intents/confirm-saved`, () =>
        HttpResponse.json({ message: 'boom' }, { status: 500 })
      )
    )
    await expect(confirmSavedMethodPayment('pi_test_123', 'pm_not_mine')).rejects.toThrow()
    expect(events).toHaveLength(0)
    cleanup()
  })
})
