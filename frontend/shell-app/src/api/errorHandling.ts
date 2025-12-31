import { useNotificationStore } from '../stores'
import { ApiClientError, isApiClientError } from './apiClient'

const HTTP_ERROR_MESSAGES: Record<number, string> = {
  0: 'Unable to connect. Please check your internet connection.',
  400: 'The request was invalid. Please check your input.',
  401: 'Please log in to continue.',
  403: 'You do not have permission to perform this action.',
  404: 'The requested resource was not found.',
  409: 'There was a conflict with the current state.',
  422: 'The submitted data could not be processed.',
  429: 'Too many requests. Please try again later.',
  500: 'An unexpected server error occurred. Please try again.',
  502: 'The server is temporarily unavailable. Please try again.',
  503: 'The service is temporarily unavailable. Please try again.',
  504: 'The request timed out. Please try again.',
}

const DEFAULT_ERROR_MESSAGE = 'An unexpected error occurred. Please try again.'

export function getHttpErrorMessage(status: number): string {
  return HTTP_ERROR_MESSAGES[status] || DEFAULT_ERROR_MESSAGE
}

export function isNetworkError(error: unknown): boolean {
  return isApiClientError(error) && error.status === 0
}

export function isServerError(error: unknown): boolean {
  return isApiClientError(error) && error.status >= 500 && error.status < 600
}

export function isClientError(error: unknown): boolean {
  return isApiClientError(error) && error.status >= 400 && error.status < 500
}

export function isUnauthorizedError(error: unknown): boolean {
  return isApiClientError(error) && error.status === 401
}

export function isForbiddenError(error: unknown): boolean {
  return isApiClientError(error) && error.status === 403
}

export function isNotFoundError(error: unknown): boolean {
  return isApiClientError(error) && error.status === 404
}

export function isValidationError(error: unknown): boolean {
  return isApiClientError(error) && (error.status === 400 || error.status === 422)
}

export function handleApiError(error: unknown): string {
  if (isApiClientError(error)) {
    const isGenericMessage =
      error.message.includes('Request failed with status code') ||
      error.message === 'Network Error'

    if (isGenericMessage) {
      return getHttpErrorMessage(error.status)
    }

    return error.message
  }

  if (error instanceof Error) {
    return error.message
  }

  return DEFAULT_ERROR_MESSAGE
}

export interface ErrorHandlerOptions {
  showNotification?: boolean
  onError?: (error: unknown, message: string) => void
  onUnauthorized?: () => void
  onForbidden?: () => void
  onNotFound?: () => void
  onNetworkError?: () => void
  onServerError?: () => void
  onValidationError?: (errors: Record<string, string[]> | null) => void
  rethrow?: boolean
}

export function createErrorHandler(options: ErrorHandlerOptions = {}) {
  const {
    showNotification = true,
    onError,
    onUnauthorized,
    onForbidden,
    onNotFound,
    onNetworkError,
    onServerError,
    onValidationError,
    rethrow = false,
  } = options

  return (error: unknown): void => {
    const message = handleApiError(error)

    if (isNetworkError(error) && onNetworkError) {
      onNetworkError()
    }

    if (isUnauthorizedError(error) && onUnauthorized) {
      onUnauthorized()
    }

    if (isForbiddenError(error) && onForbidden) {
      onForbidden()
    }

    if (isNotFoundError(error) && onNotFound) {
      onNotFound()
    }

    if (isServerError(error) && onServerError) {
      onServerError()
    }

    if (isValidationError(error) && onValidationError) {
      const validationErrors = isApiClientError(error) && error.data
        ? (error.data as { errors?: Record<string, string[]> }).errors ?? null
        : null
      onValidationError(validationErrors)
    }

    if (showNotification) {
      const store = useNotificationStore.getState()
      store.addNotification({
        type: 'error',
        message,
        duration: 5000,
      })
    }

    if (onError) {
      onError(error, message)
    }

    if (rethrow) {
      throw error
    }
  }
}

export function showErrorNotification(message: string, duration = 5000): void {
  const store = useNotificationStore.getState()
  store.addNotification({
    type: 'error',
    message,
    duration,
  })
}

export function showSuccessNotification(message: string, duration = 3000): void {
  const store = useNotificationStore.getState()
  store.addNotification({
    type: 'success',
    message,
    duration,
  })
}

export function showWarningNotification(message: string, duration = 4000): void {
  const store = useNotificationStore.getState()
  store.addNotification({
    type: 'warning',
    message,
    duration,
  })
}

export function showInfoNotification(message: string, duration = 3000): void {
  const store = useNotificationStore.getState()
  store.addNotification({
    type: 'info',
    message,
    duration,
  })
}
