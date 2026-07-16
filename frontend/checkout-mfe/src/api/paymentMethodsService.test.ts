import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { listSavedMethods } from './paymentMethodsService'
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
