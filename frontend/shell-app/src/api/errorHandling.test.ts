import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  handleApiError,
  createErrorHandler,
  getHttpErrorMessage,
  isNetworkError,
  isServerError,
  isClientError,
  isUnauthorizedError,
  isForbiddenError,
  isNotFoundError,
  isValidationError,
} from './errorHandling'
import { ApiClientError } from './apiClient'

vi.mock('../stores', () => ({
  useNotificationStore: {
    getState: vi.fn(() => ({
      addNotification: vi.fn(),
    })),
  },
}))

describe('errorHandling', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getHttpErrorMessage', () => {
    it('should return appropriate message for 400', () => {
      expect(getHttpErrorMessage(400)).toBe('The request was invalid. Please check your input.')
    })

    it('should return appropriate message for 401', () => {
      expect(getHttpErrorMessage(401)).toBe('Please log in to continue.')
    })

    it('should return appropriate message for 403', () => {
      expect(getHttpErrorMessage(403)).toBe('You do not have permission to perform this action.')
    })

    it('should return appropriate message for 404', () => {
      expect(getHttpErrorMessage(404)).toBe('The requested resource was not found.')
    })

    it('should return appropriate message for 409', () => {
      expect(getHttpErrorMessage(409)).toBe('There was a conflict with the current state.')
    })

    it('should return appropriate message for 422', () => {
      expect(getHttpErrorMessage(422)).toBe('The submitted data could not be processed.')
    })

    it('should return appropriate message for 429', () => {
      expect(getHttpErrorMessage(429)).toBe('Too many requests. Please try again later.')
    })

    it('should return appropriate message for 500', () => {
      expect(getHttpErrorMessage(500)).toBe('An unexpected server error occurred. Please try again.')
    })

    it('should return appropriate message for 502', () => {
      expect(getHttpErrorMessage(502)).toBe('The server is temporarily unavailable. Please try again.')
    })

    it('should return appropriate message for 503', () => {
      expect(getHttpErrorMessage(503)).toBe('The service is temporarily unavailable. Please try again.')
    })

    it('should return appropriate message for 504', () => {
      expect(getHttpErrorMessage(504)).toBe('The request timed out. Please try again.')
    })

    it('should return appropriate message for network error (0)', () => {
      expect(getHttpErrorMessage(0)).toBe('Unable to connect. Please check your internet connection.')
    })

    it('should return default message for unknown status', () => {
      expect(getHttpErrorMessage(418)).toBe('An unexpected error occurred. Please try again.')
    })
  })

  describe('error type checks', () => {
    describe('isNetworkError', () => {
      it('should return true for status 0', () => {
        const error = new ApiClientError(0, 'Network Error')
        expect(isNetworkError(error)).toBe(true)
      })

      it('should return false for other status codes', () => {
        const error = new ApiClientError(500, 'Server Error')
        expect(isNetworkError(error)).toBe(false)
      })

      it('should return false for non-ApiClientError', () => {
        expect(isNetworkError(new Error('Test'))).toBe(false)
      })
    })

    describe('isServerError', () => {
      it('should return true for 5xx status codes', () => {
        expect(isServerError(new ApiClientError(500, 'Error'))).toBe(true)
        expect(isServerError(new ApiClientError(502, 'Error'))).toBe(true)
        expect(isServerError(new ApiClientError(503, 'Error'))).toBe(true)
        expect(isServerError(new ApiClientError(504, 'Error'))).toBe(true)
      })

      it('should return false for non-5xx status codes', () => {
        expect(isServerError(new ApiClientError(400, 'Error'))).toBe(false)
        expect(isServerError(new ApiClientError(404, 'Error'))).toBe(false)
      })
    })

    describe('isClientError', () => {
      it('should return true for 4xx status codes', () => {
        expect(isClientError(new ApiClientError(400, 'Error'))).toBe(true)
        expect(isClientError(new ApiClientError(401, 'Error'))).toBe(true)
        expect(isClientError(new ApiClientError(404, 'Error'))).toBe(true)
        expect(isClientError(new ApiClientError(422, 'Error'))).toBe(true)
      })

      it('should return false for non-4xx status codes', () => {
        expect(isClientError(new ApiClientError(500, 'Error'))).toBe(false)
        expect(isClientError(new ApiClientError(200, 'OK'))).toBe(false)
      })
    })

    describe('isUnauthorizedError', () => {
      it('should return true for status 401', () => {
        expect(isUnauthorizedError(new ApiClientError(401, 'Unauthorized'))).toBe(true)
      })

      it('should return false for other status codes', () => {
        expect(isUnauthorizedError(new ApiClientError(403, 'Forbidden'))).toBe(false)
      })
    })

    describe('isForbiddenError', () => {
      it('should return true for status 403', () => {
        expect(isForbiddenError(new ApiClientError(403, 'Forbidden'))).toBe(true)
      })

      it('should return false for other status codes', () => {
        expect(isForbiddenError(new ApiClientError(401, 'Unauthorized'))).toBe(false)
      })
    })

    describe('isNotFoundError', () => {
      it('should return true for status 404', () => {
        expect(isNotFoundError(new ApiClientError(404, 'Not Found'))).toBe(true)
      })

      it('should return false for other status codes', () => {
        expect(isNotFoundError(new ApiClientError(400, 'Bad Request'))).toBe(false)
      })
    })

    describe('isValidationError', () => {
      it('should return true for status 400', () => {
        expect(isValidationError(new ApiClientError(400, 'Bad Request'))).toBe(true)
      })

      it('should return true for status 422', () => {
        expect(isValidationError(new ApiClientError(422, 'Unprocessable'))).toBe(true)
      })

      it('should return false for other status codes', () => {
        expect(isValidationError(new ApiClientError(500, 'Server Error'))).toBe(false)
      })
    })
  })

  describe('handleApiError', () => {
    it('should return user-friendly message for ApiClientError', () => {
      const error = new ApiClientError(404, 'Product not found')

      const result = handleApiError(error)

      expect(result).toBe('Product not found')
    })

    it('should use HTTP message when error message is generic', () => {
      const error = new ApiClientError(404, 'Request failed with status code 404')

      const result = handleApiError(error)

      expect(result).toBe('The requested resource was not found.')
    })

    it('should return default message for regular errors', () => {
      const error = new Error('Something went wrong')

      const result = handleApiError(error)

      expect(result).toBe('Something went wrong')
    })

    it('should return default message for unknown error types', () => {
      const result = handleApiError('string error')

      expect(result).toBe('An unexpected error occurred. Please try again.')
    })
  })

  describe('createErrorHandler', () => {
    it('should create error handler with default options', () => {
      const handler = createErrorHandler()

      expect(typeof handler).toBe('function')
    })

    it('should call custom onError callback', () => {
      const onError = vi.fn()
      const handler = createErrorHandler({ onError })
      const error = new ApiClientError(400, 'Bad Request')

      handler(error)

      expect(onError).toHaveBeenCalledWith(error, 'Bad Request')
    })

    it('should call onUnauthorized for 401 errors', () => {
      const onUnauthorized = vi.fn()
      const handler = createErrorHandler({ onUnauthorized })
      const error = new ApiClientError(401, 'Unauthorized')

      handler(error)

      expect(onUnauthorized).toHaveBeenCalled()
    })

    it('should call onForbidden for 403 errors', () => {
      const onForbidden = vi.fn()
      const handler = createErrorHandler({ onForbidden })
      const error = new ApiClientError(403, 'Forbidden')

      handler(error)

      expect(onForbidden).toHaveBeenCalled()
    })

    it('should call onNotFound for 404 errors', () => {
      const onNotFound = vi.fn()
      const handler = createErrorHandler({ onNotFound })
      const error = new ApiClientError(404, 'Not Found')

      handler(error)

      expect(onNotFound).toHaveBeenCalled()
    })

    it('should call onNetworkError for network errors', () => {
      const onNetworkError = vi.fn()
      const handler = createErrorHandler({ onNetworkError })
      const error = new ApiClientError(0, 'Network Error')

      handler(error)

      expect(onNetworkError).toHaveBeenCalled()
    })

    it('should call onServerError for 5xx errors', () => {
      const onServerError = vi.fn()
      const handler = createErrorHandler({ onServerError })
      const error = new ApiClientError(500, 'Server Error')

      handler(error)

      expect(onServerError).toHaveBeenCalled()
    })

    it('should rethrow error when rethrow option is true', () => {
      const handler = createErrorHandler({ rethrow: true })
      const error = new ApiClientError(400, 'Bad Request')

      expect(() => handler(error)).toThrow(error)
    })
  })
})
