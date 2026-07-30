export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface ToastDetail {
  type: ToastType
  message: string
  duration?: number
}

declare global {
  interface Window {
    __ecommerceToastHost?: boolean
  }

  interface WindowEventMap {
    'ecommerce:toast': CustomEvent<ToastDetail>
  }
}

// Standalone dev (MFE run outside the shell) has no listener for
// 'ecommerce:toast' — fall back to console so the message isn't silently lost.
const CONSOLE_FALLBACK_METHOD: Record<ToastType, 'info' | 'error'> = {
  success: 'info',
  info: 'info',
  warning: 'info',
  error: 'error',
}

function emit(type: ToastType, message: string, duration?: number): void {
  if (!window.__ecommerceToastHost) {
    console[CONSOLE_FALLBACK_METHOD[type]](`[toast:${type}] ${message}`)
    return
  }

  window.dispatchEvent(
    new CustomEvent<ToastDetail>('ecommerce:toast', { detail: { type, message, duration } })
  )
}

export const toast = {
  success: (message: string, duration?: number) => emit('success', message, duration),
  error: (message: string, duration?: number) => emit('error', message, duration),
  warning: (message: string, duration?: number) => emit('warning', message, duration),
  info: (message: string, duration?: number) => emit('info', message, duration),
}

export default toast
