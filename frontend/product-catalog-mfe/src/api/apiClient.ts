import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

declare global {
  interface Window {
    __getAuthToken?: () => Promise<string | null>
  }
}

// Use API Gateway for consistency and CORS management
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
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
