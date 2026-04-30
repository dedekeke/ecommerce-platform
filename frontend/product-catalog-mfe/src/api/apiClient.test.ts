import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import apiClient from './apiClient'

const server = setupServer(
  http.get('http://localhost:8080/api/test', () => {
    return HttpResponse.json({ ok: true })
  }),
  http.get('http://localhost:8080/api/fail', () => {
    return HttpResponse.json({ message: 'Not found' }, { status: 404 })
  })
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  delete window.__getAuthToken
})
afterAll(() => server.close())

describe('apiClient', () => {
  it('should resolve successfully for 2xx responses', async () => {
    const { data } = await apiClient.get('/test')
    expect(data).toEqual({ ok: true })
  })

  it('should reject with an error for 4xx responses', async () => {
    await expect(apiClient.get('/fail')).rejects.toThrow()
  })

  describe('auth token injection', () => {
    it('should attach Authorization header when window.__getAuthToken returns a token', async () => {
      window.__getAuthToken = vi.fn().mockResolvedValue('catalog-token-xyz')

      let capturedAuthHeader: string | undefined

      server.use(
        http.get('http://localhost:8080/api/test', ({ request }) => {
          capturedAuthHeader = request.headers.get('Authorization') ?? undefined
          return HttpResponse.json({ ok: true })
        })
      )

      await apiClient.get('/test')

      expect(window.__getAuthToken).toHaveBeenCalledOnce()
      expect(capturedAuthHeader).toBe('Bearer catalog-token-xyz')
    })

    it('should not attach Authorization header when window.__getAuthToken is not set', async () => {
      let capturedAuthHeader: string | null = null

      server.use(
        http.get('http://localhost:8080/api/test', ({ request }) => {
          capturedAuthHeader = request.headers.get('Authorization')
          return HttpResponse.json({ ok: true })
        })
      )

      await apiClient.get('/test')

      expect(capturedAuthHeader).toBeNull()
    })

    it('should not attach Authorization header when window.__getAuthToken returns null', async () => {
      window.__getAuthToken = vi.fn().mockResolvedValue(null)

      let capturedAuthHeader: string | null = null

      server.use(
        http.get('http://localhost:8080/api/test', ({ request }) => {
          capturedAuthHeader = request.headers.get('Authorization')
          return HttpResponse.json({ ok: true })
        })
      )

      await apiClient.get('/test')

      expect(capturedAuthHeader).toBeNull()
    })

    it('should proceed without Authorization header when window.__getAuthToken throws', async () => {
      window.__getAuthToken = vi.fn().mockRejectedValue(new Error('token error'))

      let capturedAuthHeader: string | null = null

      server.use(
        http.get('http://localhost:8080/api/test', ({ request }) => {
          capturedAuthHeader = request.headers.get('Authorization')
          return HttpResponse.json({ ok: true })
        })
      )

      await expect(apiClient.get('/test')).resolves.toBeDefined()
      expect(capturedAuthHeader).toBeNull()
    })
  })

  describe('retry behaviour', () => {
    it('should retry on 503 then resolve with 200', async () => {
      let callCount = 0
      server.use(
        http.get('http://localhost:8080/api/retry', () => {
          callCount += 1
          if (callCount === 1) {
            return HttpResponse.json({ message: 'Service Unavailable' }, { status: 503 })
          }
          return HttpResponse.json({ ok: true, attempt: callCount })
        })
      )

      const { data } = await apiClient.get('/retry')

      expect(callCount).toBe(2)
      expect(data).toEqual({ ok: true, attempt: 2 })
    }, 15000)
  })
})
