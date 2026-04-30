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
// network errors. POST and PATCH mutations MUST NOT be retried blindly because a
// successful-but-timed-out POST /orders request would create a duplicate order.
const IDEMPOTENT_METHODS = new Set(['get', 'head', 'options', 'put', 'delete'])

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
