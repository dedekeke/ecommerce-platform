import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import searchService, { mapSearchDocumentToProduct } from './searchService'

const API_BASE = 'http://localhost:8080/api'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('searchService.autocomplete', () => {
  it('should GET /search/autocomplete with the query param and return suggestions', async () => {
    let capturedUrl = ''
    server.use(
      http.get(`${API_BASE}/search/autocomplete`, ({ request }) => {
        capturedUrl = request.url
        return HttpResponse.json({ suggestions: ['shoes', 'shorts'] })
      }),
    )

    const suggestions = await searchService.autocomplete('sho')

    expect(new URL(capturedUrl).searchParams.get('query')).toBe('sho')
    expect(suggestions).toEqual(['shoes', 'shorts'])
  })

  it('should return an empty array without calling the API for a blank query', async () => {
    server.use(
      http.get(`${API_BASE}/search/autocomplete`, () => {
        throw new Error('should not be called')
      }),
    )

    const suggestions = await searchService.autocomplete('   ')

    expect(suggestions).toEqual([])
  })

  it('should return an empty array when the response has no suggestions field', async () => {
    server.use(
      http.get(`${API_BASE}/search/autocomplete`, () => HttpResponse.json({})),
    )

    const suggestions = await searchService.autocomplete('sh')

    expect(suggestions).toEqual([])
  })

  it('should reject when the gateway responds with an error', async () => {
    server.use(
      http.get(`${API_BASE}/search/autocomplete`, () =>
        HttpResponse.json({ message: 'boom' }, { status: 500 }),
      ),
    )

    await expect(searchService.autocomplete('sh')).rejects.toThrow()
  })
})

describe('searchService.search', () => {
  it('should GET /search with q/categories/price/page/size params', async () => {
    let capturedUrl = ''
    server.use(
      http.get(`${API_BASE}/search`, ({ request }) => {
        capturedUrl = request.url
        return HttpResponse.json({ products: [], totalElements: 0, facets: {} })
      }),
    )

    await searchService.search({
      q: 'shoes',
      categories: ['Electronics', 'Fashion'],
      minPrice: 10,
      maxPrice: 100,
      page: 1,
      size: 24,
    })

    const params = new URL(capturedUrl).searchParams
    expect(params.get('q')).toBe('shoes')
    expect(params.get('categories')).toBe('Electronics,Fashion')
    expect(params.get('minPrice')).toBe('10')
    expect(params.get('maxPrice')).toBe('100')
    expect(params.get('page')).toBe('1')
    expect(params.get('size')).toBe('24')
  })

  it('should map ProductDocument results into the Product shape used by ProductGrid', async () => {
    server.use(
      http.get(`${API_BASE}/search`, () =>
        HttpResponse.json({
          products: [
            {
              id: 'doc-1',
              name: 'Wireless Mouse',
              description: 'A mouse',
              sku: 'SKU-1',
              category: 'Electronics',
              price: 29.99,
              currency: 'USD',
              images: ['img.jpg'],
              active: true,
              stockQuantity: 5,
              createdAt: '2026-01-01T00:00:00Z',
              updatedAt: '2026-01-02T00:00:00Z',
            },
          ],
          totalElements: 1,
        }),
      ),
    )

    const result = await searchService.search({ q: 'mouse', page: 0, size: 12 })

    expect(result.totalElements).toBe(1)
    expect(result.totalPages).toBe(1)
    expect(result.products).toHaveLength(1)
    expect(result.products[0]).toMatchObject({
      id: 'doc-1',
      name: 'Wireless Mouse',
      price: 29.99,
      inStock: true,
      available: true,
    })
    expect(result.products[0].category?.name).toBe('Electronics')
  })

  it('should mark out-of-stock documents correctly', async () => {
    server.use(
      http.get(`${API_BASE}/search`, () =>
        HttpResponse.json({
          products: [
            {
              id: 'doc-2',
              name: 'Sold Out Widget',
              price: 9.99,
              stockQuantity: 0,
              active: true,
            },
          ],
          totalElements: 1,
        }),
      ),
    )

    const result = await searchService.search({ q: 'widget' })

    expect(result.products[0].inStock).toBe(false)
    expect(result.products[0].available).toBe(false)
    expect(result.products[0].category).toBeNull()
  })

  it('should reject when the gateway responds with an error', async () => {
    server.use(
      http.get(`${API_BASE}/search`, () =>
        HttpResponse.json({ message: 'boom' }, { status: 500 }),
      ),
    )

    await expect(searchService.search({ q: 'x' })).rejects.toThrow()
  })
})

describe('mapSearchDocumentToProduct', () => {
  it('should default missing optional fields', () => {
    const product = mapSearchDocumentToProduct({ id: '1', name: 'Test', price: 1 })

    expect(product.images).toEqual([])
    expect(product.currency).toBe('USD')
    expect(product.stockQuantity).toBe(0)
    expect(product.category).toBeNull()
  })
})
