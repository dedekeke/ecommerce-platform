import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios'
import axiosRetry from 'axios-retry'

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

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (import.meta.env.DEV) {
      console.error('API Error:', error.response?.data || error.message)
    }
    return Promise.reject(error)
  }
)

export default apiClient
