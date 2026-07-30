import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios'
import axiosRetry from 'axios-retry'
import { toast } from '../lib/toast'

declare global {
  interface Window {
    __getAuthToken?: () => Promise<string | null>
  }
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// SECURITY: Only retry idempotent HTTP methods (GET, HEAD, OPTIONS, PUT, DELETE) on 5xx /
// network errors by default. POST and PATCH mutations MUST NOT be retried blindly because a
// successful-but-timed-out mutation could be re-applied.
const IDEMPOTENT_METHODS = new Set(['get', 'head', 'options', 'put', 'delete'])

// Checkout is the one deliberate exception: POST /orders is guarded by a client-generated
// Idempotency-Key (see checkoutStore/orderService) that the backend uses to dedupe, and the
// contract explicitly reserves 502 for transient saga/downstream failures that are safe to
// retry with that same key (see order-service OrderApiExceptionHandler / PR#122). 409 (a
// checkout with this key is already in flight) is deliberately excluded — retrying that would
// just re-trigger the concurrent-double-submit guard.
const isRetryableOrderCheckout = (error: AxiosError): boolean => {
  const method = (error.config?.method ?? '').toLowerCase()
  const url = error.config?.url ?? ''
  return method === 'post' && url.includes('/orders') && error.response?.status === 502
}

axiosRetry(apiClient, {
  retries: 3,
  retryDelay: axiosRetry.exponentialDelay,
  retryCondition: (error: AxiosError) => {
    const method = (error.config?.method ?? '').toLowerCase()
    const isIdempotent = IDEMPOTENT_METHODS.has(method)
    const status = error.response?.status

    if (axiosRetry.isNetworkOrIdempotentRequestError(error)) return true
    if (status === 429 && isIdempotent) return true
    if (status !== undefined && status >= 500 && isIdempotent) return true
    if (isRetryableOrderCheckout(error)) return true

    return false
  },
  onRetry: (retryCount, error) => {
    if (import.meta.env.DEV) {
      console.log(
        `[API Retry] Attempt ${retryCount} for ${error.config?.url}`,
        {
          status: error.response?.status,
          message: error.message,
        }
      )
    }
  },
})

apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig): Promise<InternalAxiosRequestConfig> => {
    if (typeof window.__getAuthToken === 'function') {
      try {
        const token = await window.__getAuthToken()
        if (token) {
          config.headers.Authorization = `Bearer ${token}`
        }
      } catch {
        // proceed without auth header if token acquisition fails
      }
    }
    return config
  }
)

// Statuses the shell already surfaces its own toast for (session expiry / permissions) — never
// double-toast those here.
const SILENT_STATUSES = new Set([401, 403])

const STATUS_ERROR_MESSAGES: Record<number, string> = {
  400: 'That request was invalid. Please check your input and try again.',
  404: 'The requested resource could not be found.',
  409: 'This action conflicts with the current state. Please refresh and try again.',
  422: 'Some information could not be processed. Please check your input.',
  429: 'Too many requests. Please wait a moment and try again.',
  500: 'Something went wrong on our end. Please try again.',
  502: 'Something went wrong on our end. Please try again.',
  503: 'Service is temporarily unavailable. Please try again shortly.',
}

const DEFAULT_ERROR_MESSAGE = 'Request failed. Please try again.'

function resolveErrorMessage(error: AxiosError): string {
  const data = error.response?.data as { message?: string; error?: string } | undefined
  if (data?.message) return data.message
  if (data?.error) return data.error

  const status = error.response?.status
  if (status && STATUS_ERROR_MESSAGES[status]) return STATUS_ERROR_MESSAGES[status]

  return DEFAULT_ERROR_MESSAGE
}

// axios-retry re-dispatches the request through the FULL interceptor chain on every retry
// attempt, so this handler is invoked once per nesting level for the SAME final error as it
// propagates back up. Track already-toasted errors so a retried request only toasts once.
const toastedErrors = new WeakSet<object>()

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (import.meta.env.DEV) {
      console.error('API Error:', error.response?.data || error.message)
    }

    const status = error.response?.status
    if (
      !toastedErrors.has(error) &&
      !axios.isCancel(error) &&
      (status === undefined || !SILENT_STATUSES.has(status))
    ) {
      toast.error(resolveErrorMessage(error))
      toastedErrors.add(error)
    }

    return Promise.reject(error)
  }
)

export default apiClient
