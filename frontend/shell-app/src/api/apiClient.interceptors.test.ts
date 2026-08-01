import { describe, it, expect, vi, afterEach } from 'vitest'
import axios from 'axios'
import {
  apiClient,
  setupAuthInterceptor,
  removeAuthInterceptor,
  setupResponseInterceptor,
  removeResponseInterceptor,
  ApiClientError,
} from './apiClient'

/**
 * Behaviour tests for the auth + response interceptors.
 * These exercise the runtime paths not covered by the basic "callable" tests.
 */

describe('setupAuthInterceptor behaviour', () => {
  let interceptorId: number

  afterEach(() => {
    try {
      removeAuthInterceptor(interceptorId)
    } catch {
      // interceptor may already be removed; safe to ignore
    }
  })

  it('attaches Bearer token when getAccessToken resolves a token', async () => {
    const mockGetToken = vi.fn().mockResolvedValue('test-jwt')
    interceptorId = setupAuthInterceptor(mockGetToken)

    const config = await (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled: (c: object) => Promise<object> }>
    }).handlers
      .at(-1)!
      .fulfilled({ url: '/test', method: 'get', headers: {} as Record<string, string> })

    expect((config as { headers: Record<string, string> }).headers.Authorization).toBe('Bearer test-jwt')
  })

  it('skips adding token when skipAuth is true', async () => {
    const mockGetToken = vi.fn().mockResolvedValue('test-jwt')
    interceptorId = setupAuthInterceptor(mockGetToken)

    const config = await (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled: (c: object) => Promise<object> }>
    }).handlers
      .at(-1)!
      .fulfilled({ url: '/test', method: 'get', headers: {} as Record<string, string>, skipAuth: true })

    expect((config as { headers: Record<string, string> }).headers.Authorization).toBeUndefined()
  })

  it('proceeds without Authorization header when getAccessToken returns null', async () => {
    const mockGetToken = vi.fn().mockResolvedValue(null)
    interceptorId = setupAuthInterceptor(mockGetToken)

    const config = await (apiClient.interceptors.request as unknown as {
      handlers: Array<{ fulfilled: (c: object) => Promise<object> }>
    }).handlers
      .at(-1)!
      .fulfilled({ url: '/test', method: 'get', headers: {} as Record<string, string> })

    expect((config as { headers: Record<string, string> }).headers.Authorization).toBeUndefined()
  })

  it('proceeds without throwing when getAccessToken rejects', async () => {
    const mockGetToken = vi.fn().mockRejectedValue(new Error('Token fetch error'))
    interceptorId = setupAuthInterceptor(mockGetToken)

    await expect(
      (apiClient.interceptors.request as unknown as {
        handlers: Array<{ fulfilled: (c: object) => Promise<object> }>
      }).handlers
        .at(-1)!
        .fulfilled({ url: '/test', method: 'get', headers: {} as Record<string, string> })
    ).resolves.not.toThrow()
  })
})

describe('setupResponseInterceptor behaviour', () => {
  let interceptorId: number

  afterEach(() => {
    try {
      removeResponseInterceptor(interceptorId)
    } catch {
      // interceptor may already be removed; safe to ignore
    }
  })

  it('passes through successful responses unchanged', async () => {
    const onUnauthorized = vi.fn()
    const onForbidden = vi.fn()
    interceptorId = setupResponseInterceptor(onUnauthorized, onForbidden)

    const fakeResponse = {
      status: 200,
      data: { id: 1 },
      config: { method: 'get', url: '/api/test' },
    }

    const handler = (apiClient.interceptors.response as unknown as {
      handlers: Array<{ fulfilled: (r: object) => object }>
    }).handlers.at(-1)!

    const result = handler.fulfilled(fakeResponse)
    expect(result).toBe(fakeResponse)
    expect(onUnauthorized).not.toHaveBeenCalled()
    expect(onForbidden).not.toHaveBeenCalled()
  })

  it('calls onUnauthorized callback on 401 error', async () => {
    const onUnauthorized = vi.fn()
    const onForbidden = vi.fn()
    interceptorId = setupResponseInterceptor(onUnauthorized, onForbidden)

    const axiosError = new axios.AxiosError('Unauthorized', '401', undefined, undefined, {
      status: 401,
      data: { message: 'Unauthorized' },
      headers: {},
      config: { method: 'get', url: '/api/protected', headers: {} as never },
      statusText: 'Unauthorized',
    })

    const handler = (apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected: (e: Error) => Promise<never> }>
    }).handlers.at(-1)!

    await expect(handler.rejected(axiosError)).rejects.toBeInstanceOf(ApiClientError)
    expect(onUnauthorized).toHaveBeenCalledOnce()
    expect(onForbidden).not.toHaveBeenCalled()
  })

  it('calls onForbidden callback on 403 error', async () => {
    const onUnauthorized = vi.fn()
    const onForbidden = vi.fn()
    interceptorId = setupResponseInterceptor(onUnauthorized, onForbidden)

    const axiosError = new axios.AxiosError('Forbidden', '403', undefined, undefined, {
      status: 403,
      data: { message: 'Forbidden' },
      headers: {},
      config: { method: 'get', url: '/api/admin', headers: {} as never },
      statusText: 'Forbidden',
    })

    const handler = (apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected: (e: Error) => Promise<never> }>
    }).handlers.at(-1)!

    await expect(handler.rejected(axiosError)).rejects.toBeInstanceOf(ApiClientError)
    expect(onForbidden).toHaveBeenCalledOnce()
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('rejects with ApiClientError on any HTTP error without triggering callbacks', async () => {
    const onUnauthorized = vi.fn()
    interceptorId = setupResponseInterceptor(onUnauthorized)

    const axiosError = new axios.AxiosError('Server Error', '500', undefined, undefined, {
      status: 500,
      data: { message: 'Internal Server Error' },
      headers: {},
      config: { method: 'post', url: '/api/orders', headers: {} as never },
      statusText: 'Internal Server Error',
    })

    const handler = (apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected: (e: Error) => Promise<never> }>
    }).handlers.at(-1)!

    await expect(handler.rejected(axiosError)).rejects.toBeInstanceOf(ApiClientError)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('works when callbacks are not provided', async () => {
    interceptorId = setupResponseInterceptor()

    const axiosError = new axios.AxiosError('Unauthorized', '401', undefined, undefined, {
      status: 401,
      data: {},
      headers: {},
      config: { method: 'get', url: '/api/me', headers: {} as never },
      statusText: 'Unauthorized',
    })

    const handler = (apiClient.interceptors.response as unknown as {
      handlers: Array<{ rejected: (e: Error) => Promise<never> }>
    }).handlers.at(-1)!

    await expect(handler.rejected(axiosError)).rejects.toBeInstanceOf(ApiClientError)
  })
})
