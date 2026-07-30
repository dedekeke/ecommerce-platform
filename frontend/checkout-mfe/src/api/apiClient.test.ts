import { describe, it, expect, beforeAll, beforeEach, afterAll, afterEach, vi } from 'vitest'
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
      window.__getAuthToken = vi.fn().mockResolvedValue('checkout-token-abc')

      let capturedAuthHeader: string | undefined

      server.use(
        http.get('http://localhost:8080/api/test', ({ request }) => {
          capturedAuthHeader = request.headers.get('Authorization') ?? undefined
          return HttpResponse.json({ ok: true })
        })
      )

      await apiClient.get('/test')

      expect(window.__getAuthToken).toHaveBeenCalledOnce()
      expect(capturedAuthHeader).toBe('Bearer checkout-token-abc')
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

    it('should NOT retry a generic POST on a 5xx (no Idempotency-Key contract for that endpoint)', async () => {
      let callCount = 0
      server.use(
        http.post('http://localhost:8080/api/widgets', () => {
          callCount += 1
          return HttpResponse.json({ message: 'boom' }, { status: 500 })
        })
      )

      await expect(apiClient.post('/widgets', {})).rejects.toThrow()
      expect(callCount).toBe(1)
    })

    it('should retry POST /orders specifically on a 502 (order-first checkout saga failure)', async () => {
      let callCount = 0
      server.use(
        http.post('http://localhost:8080/api/orders', () => {
          callCount += 1
          if (callCount === 1) {
            return HttpResponse.json({ message: 'saga/downstream failure' }, { status: 502 })
          }
          return HttpResponse.json({ ok: true, attempt: callCount }, { status: 201 })
        })
      )

      const { data } = await apiClient.post('/orders', {})

      expect(callCount).toBe(2)
      expect(data).toEqual({ ok: true, attempt: 2 })
    }, 15000)

    it('should NOT retry POST /orders on a 409 (checkout already in flight for this key)', async () => {
      let callCount = 0
      server.use(
        http.post('http://localhost:8080/api/orders', () => {
          callCount += 1
          return HttpResponse.json({ message: 'in flight' }, { status: 409 })
        })
      )

      await expect(apiClient.post('/orders', {})).rejects.toThrow()
      expect(callCount).toBe(1)
    })

    it('should NOT retry POST /orders on a plain 500 (only 502 is the documented retryable band)', async () => {
      let callCount = 0
      server.use(
        http.post('http://localhost:8080/api/orders', () => {
          callCount += 1
          return HttpResponse.json({ message: 'unexpected' }, { status: 500 })
        })
      )

      await expect(apiClient.post('/orders', {})).rejects.toThrow()
      expect(callCount).toBe(1)
    })
  })

  describe('error toasting', () => {
    beforeEach(() => {
      window.__ecommerceToastHost = true
    })
    afterEach(() => {
      delete window.__ecommerceToastHost
    })

    function captureToasts() {
      const events: CustomEvent[] = []
      const listener = (e: Event) => events.push(e as CustomEvent)
      window.addEventListener('ecommerce:toast', listener)
      return {
        events,
        cleanup: () => window.removeEventListener('ecommerce:toast', listener),
      }
    }

    it('should toast the server-provided message on a 5xx response', async () => {
      const { events, cleanup } = captureToasts()
      server.use(
        http.post('http://localhost:8080/api/widgets', () =>
          HttpResponse.json({ message: 'Inventory service is down' }, { status: 500 })
        )
      )

      await expect(apiClient.post('/widgets', {})).rejects.toThrow()

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toEqual({
        type: 'error',
        message: 'Inventory service is down',
        duration: undefined,
      })
      cleanup()
    })

    it('should fall back to a generic per-status message when the server sends no message/error field', async () => {
      const { events, cleanup } = captureToasts()
      server.use(
        http.post('http://localhost:8080/api/widgets', () => HttpResponse.json({}, { status: 500 }))
      )

      await expect(apiClient.post('/widgets', {})).rejects.toThrow()

      expect(events[0]?.detail).toMatchObject({ type: 'error' })
      expect((events[0]?.detail as { message: string }).message).toMatch(/try again/i)
      cleanup()
    })

    it('should prefer the `error` field when `message` is absent', async () => {
      const { events, cleanup } = captureToasts()
      server.use(
        http.post('http://localhost:8080/api/widgets', () =>
          HttpResponse.json({ error: 'Duplicate SKU' }, { status: 400 })
        )
      )

      await expect(apiClient.post('/widgets', {})).rejects.toThrow()

      expect(events[0]?.detail).toMatchObject({ type: 'error', message: 'Duplicate SKU' })
      cleanup()
    })

    it('should NOT toast on a 401 (session-expiry is handled by the shell)', async () => {
      const { events, cleanup } = captureToasts()
      server.use(
        http.get('http://localhost:8080/api/test', () =>
          HttpResponse.json({ message: 'Unauthorized' }, { status: 401 })
        )
      )

      await expect(apiClient.get('/test')).rejects.toThrow()

      expect(events).toHaveLength(0)
      cleanup()
    })

    it('should NOT toast on a 403 (permission errors are handled by the shell)', async () => {
      const { events, cleanup } = captureToasts()
      server.use(
        http.get('http://localhost:8080/api/test', () =>
          HttpResponse.json({ message: 'Forbidden' }, { status: 403 })
        )
      )

      await expect(apiClient.get('/test')).rejects.toThrow()

      expect(events).toHaveLength(0)
      cleanup()
    })

    it('should NOT toast when the request was cancelled', async () => {
      const { events, cleanup } = captureToasts()
      const controller = new AbortController()

      const promise = apiClient.get('/test', { signal: controller.signal })
      controller.abort()

      await expect(promise).rejects.toThrow()
      expect(events).toHaveLength(0)
      cleanup()
    })

    it('should toast only once after checkout exhausts its 502 retries and still fails', async () => {
      const { events, cleanup } = captureToasts()
      server.use(
        http.post('http://localhost:8080/api/orders', () =>
          HttpResponse.json({ message: 'saga/downstream failure' }, { status: 502 })
        )
      )

      await expect(apiClient.post('/orders', {})).rejects.toThrow()

      expect(events).toHaveLength(1)
      expect(events[0]?.detail).toMatchObject({
        type: 'error',
        message: 'saga/downstream failure',
      })
      cleanup()
    }, 15000)

    it('should still reject the promise so callers keep their existing error paths', async () => {
      server.use(
        http.post('http://localhost:8080/api/widgets', () =>
          HttpResponse.json({ message: 'boom' }, { status: 500 })
        )
      )

      await expect(apiClient.post('/widgets', {})).rejects.toMatchObject({
        response: { status: 500 },
      })
    })
  })
})
