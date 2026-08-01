import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  apiClient,
  ApiClientError,
  isApiClientError,
  getErrorMessage,
  getValidationErrors,
  logRequest,
  logResponse,
} from './apiClient'

describe('apiClient', () => {
  describe('configuration', () => {
    it('should have baseURL configured', () => {
      expect(apiClient.defaults.baseURL).toBeDefined()
    })

    it('should have timeout configured', () => {
      expect(apiClient.defaults.timeout).toBe(30000)
    })

    it('should have Content-Type header set to application/json', () => {
      expect(apiClient.defaults.headers['Content-Type']).toBe('application/json')
    })

    it('should have Accept header set to application/json', () => {
      expect(apiClient.defaults.headers['Accept']).toBe('application/json')
    })
  })

  describe('ApiClientError', () => {
    it('should create error with status and message', () => {
      const error = new ApiClientError(404, 'Not Found')

      expect(error.status).toBe(404)
      expect(error.message).toBe('Not Found')
      expect(error.name).toBe('ApiClientError')
    })

    it('should create error with optional data', () => {
      const errorData = { field: 'email', error: 'Invalid format' }
      const error = new ApiClientError(400, 'Validation Error', errorData)

      expect(error.data).toEqual(errorData)
    })

    it('should be instanceof Error', () => {
      const error = new ApiClientError(500, 'Server Error')

      expect(error).toBeInstanceOf(Error)
    })

    it('should have correct prototype chain', () => {
      const error = new ApiClientError(500, 'Server Error')

      expect(error).toBeInstanceOf(ApiClientError)
    })
  })

  describe('isApiClientError', () => {
    it('should return true for ApiClientError instance', () => {
      const error = new ApiClientError(404, 'Not Found')

      expect(isApiClientError(error)).toBe(true)
    })

    it('should return false for regular Error', () => {
      const error = new Error('Regular error')

      expect(isApiClientError(error)).toBe(false)
    })

    it('should return false for null', () => {
      expect(isApiClientError(null)).toBe(false)
    })

    it('should return false for undefined', () => {
      expect(isApiClientError(undefined)).toBe(false)
    })

    it('should return false for plain objects', () => {
      expect(isApiClientError({ status: 404, message: 'Not Found' })).toBe(false)
    })
  })

  describe('getErrorMessage', () => {
    it('should return message from ApiClientError', () => {
      const error = new ApiClientError(400, 'Bad Request')

      expect(getErrorMessage(error)).toBe('Bad Request')
    })

    it('should return message from regular Error', () => {
      const error = new Error('Something went wrong')

      expect(getErrorMessage(error)).toBe('Something went wrong')
    })

    it('should return default message for non-error values', () => {
      expect(getErrorMessage('string error')).toBe('An unexpected error occurred')
      expect(getErrorMessage(123)).toBe('An unexpected error occurred')
      expect(getErrorMessage(null)).toBe('An unexpected error occurred')
    })
  })

  describe('getValidationErrors', () => {
    it('should return validation errors from ApiClientError data', () => {
      const validationErrors = {
        email: ['Invalid email format'],
        password: ['Too short', 'Missing special character'],
      }
      const error = new ApiClientError(400, 'Validation Error', {
        errors: validationErrors,
      })

      expect(getValidationErrors(error)).toEqual(validationErrors)
    })

    it('should return null when ApiClientError has no data', () => {
      const error = new ApiClientError(400, 'Bad Request')

      expect(getValidationErrors(error)).toBeNull()
    })

    it('should return null when data has no errors field', () => {
      const error = new ApiClientError(400, 'Bad Request', { message: 'Error' })

      expect(getValidationErrors(error)).toBeNull()
    })

    it('should return null for non-ApiClientError', () => {
      const error = new Error('Regular error')

      expect(getValidationErrors(error)).toBeNull()
    })
  })
})

describe('logging functions', () => {
  let consoleLogSpy: ReturnType<typeof vi.spyOn>
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    consoleLogSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleLogSpy.mockRestore()
    consoleErrorSpy.mockRestore()
  })

  describe('logRequest', () => {
    it('should log request details in development mode', () => {
      logRequest('GET', '/api/products', { page: 1 })

      if (import.meta.env.DEV) {
        expect(consoleLogSpy).toHaveBeenCalledWith(
          '[API Request]',
          expect.objectContaining({
            method: 'GET',
            url: '/api/products',
            timestamp: expect.any(String),
          })
        )
      }
    })

    it('should truncate long data in logs', () => {
      const longData = { data: 'x'.repeat(300) }
      logRequest('POST', '/api/products', longData)

      if (import.meta.env.DEV) {
        const loggedData = consoleLogSpy.mock.calls[0]?.[1]
        expect(loggedData?.data?.length).toBeLessThanOrEqual(200)
      }
    })

    it('should handle undefined data', () => {
      logRequest('GET', '/api/products')

      if (import.meta.env.DEV) {
        expect(consoleLogSpy).toHaveBeenCalledWith(
          '[API Request]',
          expect.objectContaining({
            data: undefined,
          })
        )
      }
    })
  })

  describe('logResponse', () => {
    it('should log response details in development mode', () => {
      logResponse('GET', '/api/products', 200, { data: [] })

      if (import.meta.env.DEV) {
        expect(consoleLogSpy).toHaveBeenCalledWith(
          '[API Response]',
          expect.objectContaining({
            method: 'GET',
            url: '/api/products',
            status: 200,
            timestamp: expect.any(String),
          })
        )
      }
    })

    it('should use console.error for error responses', () => {
      logResponse('GET', '/api/products', 500, { error: 'Server Error' }, true)

      if (import.meta.env.DEV) {
        expect(consoleErrorSpy).toHaveBeenCalledWith(
          '[API Response]',
          expect.objectContaining({
            status: 500,
          })
        )
      }
    })
  })
})

describe('interceptor setup functions', () => {
  it('setupAuthInterceptor should be importable and callable', async () => {
    const { setupAuthInterceptor } = await import('./apiClient')
    const mockGetToken = vi.fn().mockResolvedValue('token')

    const interceptorId = setupAuthInterceptor(mockGetToken)

    expect(typeof interceptorId).toBe('number')
  })

  it('setupResponseInterceptor should be importable and callable', async () => {
    const { setupResponseInterceptor } = await import('./apiClient')
    const mockOnUnauthorized = vi.fn()
    const mockOnForbidden = vi.fn()

    const interceptorId = setupResponseInterceptor(
      mockOnUnauthorized,
      mockOnForbidden
    )

    expect(typeof interceptorId).toBe('number')
  })

  it('removeAuthInterceptor should be importable and callable', async () => {
    const { removeAuthInterceptor, setupAuthInterceptor } = await import(
      './apiClient'
    )
    const mockGetToken = vi.fn().mockResolvedValue('token')
    const interceptorId = setupAuthInterceptor(mockGetToken)

    expect(() => removeAuthInterceptor(interceptorId)).not.toThrow()
  })

  it('removeResponseInterceptor should be importable and callable', async () => {
    const { removeResponseInterceptor, setupResponseInterceptor } = await import(
      './apiClient'
    )
    const interceptorId = setupResponseInterceptor()

    expect(() => removeResponseInterceptor(interceptorId)).not.toThrow()
  })
})
