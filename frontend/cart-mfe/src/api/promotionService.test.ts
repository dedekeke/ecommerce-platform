import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers, mockValidDiscount, mockInvalidDiscount } from '../test/mocks'
import { validatePromotion } from './promotionService'

const API_BASE = 'http://localhost:8080/api'
const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('promotionService', () => {
  describe('validatePromotion', () => {
    it('should return a valid discount result for a valid code', async () => {
      const result = await validatePromotion({ code: 'SAVE10', purchaseAmount: 100 })
      expect(result).toEqual(mockValidDiscount)
    })

    it('should return an invalid discount result for an unknown code', async () => {
      const result = await validatePromotion({ code: 'NOPE', purchaseAmount: 100 })
      expect(result).toEqual(mockInvalidDiscount)
    })

    it('should send the code and purchaseAmount in the request body', async () => {
      let capturedBody: unknown
      server.use(
        http.post(`${API_BASE}/promotions/validate`, async ({ request }) => {
          capturedBody = await request.json()
          return HttpResponse.json(mockValidDiscount)
        })
      )

      await validatePromotion({ code: 'SAVE10', purchaseAmount: 42.5 })

      expect(capturedBody).toEqual({ code: 'SAVE10', purchaseAmount: 42.5 })
    })

    it('should reject on a server error without triggering a second toast path', async () => {
      server.use(
        http.post(`${API_BASE}/promotions/validate`, () =>
          HttpResponse.json({ message: 'Promotion service is down' }, { status: 500 })
        )
      )

      await expect(validatePromotion({ code: 'SAVE10', purchaseAmount: 100 })).rejects.toThrow()
    })
  })
})
