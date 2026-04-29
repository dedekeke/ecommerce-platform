import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
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
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('apiClient', () => {
  it('should resolve successfully for 2xx responses', async () => {
    const { data } = await apiClient.get('/test')
    expect(data).toEqual({ ok: true })
  })

  it('should reject with an error for 4xx responses', async () => {
    await expect(apiClient.get('/fail')).rejects.toThrow()
  })
})
