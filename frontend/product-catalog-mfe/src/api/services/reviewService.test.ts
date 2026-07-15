import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import reviewService from './reviewService'

const API_BASE = 'http://localhost:8080/api'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('reviewService', () => {
  describe('getSummary', () => {
    it('should GET the v1 gateway summary endpoint for the given product', async () => {
      let capturedUrl = ''
      server.use(
        http.get(`${API_BASE}/v1/reviews/product/:productId/summary`, ({ request, params }) => {
          capturedUrl = request.url
          expect(params.productId).toBe('prod-1')
          return HttpResponse.json({
            averageRating: 4.5,
            count: 2,
            distribution: { '4': 1, '5': 1 },
          })
        }),
      )

      const summary = await reviewService.getSummary('prod-1')

      expect(capturedUrl).toBe(`${API_BASE}/v1/reviews/product/prod-1/summary`)
      expect(summary).toEqual({ averageRating: 4.5, count: 2, distribution: { '4': 1, '5': 1 } })
    })
  })

  describe('listByProduct', () => {
    it('should GET the v1 gateway list endpoint with page/size/sort params', async () => {
      let captured: URLSearchParams | undefined
      server.use(
        http.get(`${API_BASE}/v1/reviews/product/:productId`, ({ request }) => {
          captured = new URL(request.url).searchParams
          return HttpResponse.json({
            content: [],
            page: 1,
            size: 5,
            totalElements: 0,
            totalPages: 0,
            sort: 'recent',
          })
        }),
      )

      const page = await reviewService.listByProduct('prod-1', 1, 5, 'recent')

      expect(captured?.get('page')).toBe('1')
      expect(captured?.get('size')).toBe('5')
      expect(captured?.get('sort')).toBe('recent')
      expect(page.totalPages).toBe(0)
    })

    it('should default to page 0, size 5, sort helpful', async () => {
      let captured: URLSearchParams | undefined
      server.use(
        http.get(`${API_BASE}/v1/reviews/product/:productId`, ({ request }) => {
          captured = new URL(request.url).searchParams
          return HttpResponse.json({
            content: [],
            page: 0,
            size: 5,
            totalElements: 0,
            totalPages: 0,
            sort: 'helpful',
          })
        }),
      )

      await reviewService.listByProduct('prod-1')

      expect(captured?.get('page')).toBe('0')
      expect(captured?.get('size')).toBe('5')
      expect(captured?.get('sort')).toBe('helpful')
    })
  })

  describe('createReview', () => {
    it('should POST the payload to the v1 gateway reviews endpoint', async () => {
      let capturedBody: unknown
      server.use(
        http.post(`${API_BASE}/v1/reviews`, async ({ request }) => {
          capturedBody = await request.json()
          return HttpResponse.json(
            {
              id: 'rev-1',
              productId: 'prod-1',
              userId: 'auth0|abc',
              rating: 5,
              title: 'Great',
              body: 'Loved it',
              verified: false,
              helpful: 0,
              createdAt: '2026-01-01T00:00:00Z',
            },
            { status: 201 },
          )
        }),
      )

      const payload = { productId: 'prod-1', rating: 5, title: 'Great', body: 'Loved it' }
      const review = await reviewService.createReview(payload)

      expect(capturedBody).toEqual(payload)
      expect(review.id).toBe('rev-1')
    })

    it('should reject when the gateway responds 401 (unauthenticated write)', async () => {
      server.use(
        http.post(`${API_BASE}/v1/reviews`, () => {
          return HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })
        }),
      )

      await expect(
        reviewService.createReview({ productId: 'prod-1', rating: 1, title: 't', body: 'b' }),
      ).rejects.toThrow()
    })
  })
})
