import axios, {
  AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios'
import axiosRetry from 'axios-retry'

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
const DEFAULT_TIMEOUT = 30000

export class ApiClientError extends Error {
  public readonly status: number
  public readonly data?: unknown

  constructor(status: number, message: string, data?: unknown) {
    super(message)
    this.name = 'ApiClientError'
    this.status = status
    this.data = data
    Object.setPrototypeOf(this, ApiClientError.prototype)
  }
}

export function isApiClientError(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: DEFAULT_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

// SECURITY: Only retry idempotent HTTP methods (GET, HEAD, OPTIONS, PUT, DELETE) on 5xx /
// network errors. POST and PATCH mutations MUST NOT be retried blindly because a
// successful-but-timed-out POST request could cause duplicate side-effects (e.g. duplicate orders).
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

type GetAccessTokenFn = () => Promise<string | null>

export function setupAuthInterceptor(getAccessToken: GetAccessTokenFn): number {
  return apiClient.interceptors.request.use(
    async (
      config: InternalAxiosRequestConfig & { skipAuth?: boolean }
    ): Promise<InternalAxiosRequestConfig> => {
      if (config.skipAuth) {
        return config
      }

      try {
        const token = await getAccessToken()
        if (token) {
          config.headers.Authorization = `Bearer ${token}`
        }
      } catch (error) {
        if (import.meta.env.DEV) {
          console.warn('[API] Failed to get access token:', error)
        }
      }

      logRequest(config.method?.toUpperCase() || 'UNKNOWN', config.url || '', config.data)

      return config
    },
    (error) => {
      return Promise.reject(error)
    }
  )
}

export function removeAuthInterceptor(interceptorId: number): void {
  apiClient.interceptors.request.eject(interceptorId)
}

type ErrorCallback = () => void

export function setupResponseInterceptor(
  onUnauthorized?: ErrorCallback,
  onForbidden?: ErrorCallback
): number {
  return apiClient.interceptors.response.use(
    (response) => {
      logResponse(
        response.config.method?.toUpperCase() || 'UNKNOWN',
        response.config.url || '',
        response.status,
        response.data
      )
      return response
    },
    (error: AxiosError<{ message?: string; errors?: Record<string, string[]> }>) => {
      const status = error.response?.status || 0
      const responseData = error.response?.data
      const message = responseData?.message || error.message || 'Unknown error'

      logResponse(
        error.config?.method?.toUpperCase() || 'UNKNOWN',
        error.config?.url || '',
        status,
        responseData,
        true
      )

      if (status === 401 && onUnauthorized) {
        onUnauthorized()
      }

      if (status === 403 && onForbidden) {
        onForbidden()
      }

      return Promise.reject(new ApiClientError(status, message, responseData))
    }
  )
}

export function removeResponseInterceptor(interceptorId: number): void {
  apiClient.interceptors.response.eject(interceptorId)
}

export function logRequest(
  method: string,
  url: string,
  data?: unknown
): void {
  if (!import.meta.env.DEV) return

  console.log('[API Request]', {
    method,
    url,
    data: data ? JSON.stringify(data).substring(0, 200) : undefined,
    timestamp: new Date().toISOString(),
  })
}

export function logResponse(
  method: string,
  url: string,
  status: number,
  data?: unknown,
  isError = false
): void {
  if (!import.meta.env.DEV) return

  const logFn = isError ? console.error : console.log
  logFn(`[API Response]`, {
    method,
    url,
    status,
    data: data ? JSON.stringify(data).substring(0, 200) : undefined,
    timestamp: new Date().toISOString(),
  })
}

export function getErrorMessage(error: unknown): string {
  if (isApiClientError(error)) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'An unexpected error occurred'
}

export function getValidationErrors(
  error: unknown
): Record<string, string[]> | null {
  if (
    isApiClientError(error) &&
    error.data &&
    typeof error.data === 'object' &&
    'errors' in error.data
  ) {
    return (error.data as { errors: Record<string, string[]> }).errors
  }
  return null
}

export default apiClient
