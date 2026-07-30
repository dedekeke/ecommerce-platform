import { useEffect } from 'react'
import { useNotificationStore } from '../stores'
import type { NotificationType } from '../stores'

export const TOAST_EVENT = 'ecommerce:toast'

const VALID_TYPES: NotificationType[] = ['success', 'error', 'warning', 'info']

interface ToastEventDetail {
  type: NotificationType
  message: string
  duration?: number
}

function isValidToastDetail(detail: unknown): detail is ToastEventDetail {
  if (!detail || typeof detail !== 'object') return false
  const { type, message, duration } = detail as Record<string, unknown>

  if (typeof message !== 'string' || message.trim().length === 0) return false
  if (!VALID_TYPES.includes(type as NotificationType)) return false
  if (duration !== undefined && typeof duration !== 'number') return false

  return true
}

/**
 * Bridges toast requests from federated MFEs (which cannot import the shell's zustand store
 * directly) into the shell's notification store via a `window` CustomEvent. MFEs dispatch
 * `new CustomEvent(TOAST_EVENT, { detail: { type, message, duration? } })`; malformed details
 * are silently ignored. `window.__ecommerceToastHost` lets MFEs detect a host is listening.
 */
export function useToastBridge(): void {
  const addNotification = useNotificationStore((state) => state.addNotification)

  useEffect(() => {
    window.__ecommerceToastHost = true

    const handleToastEvent = (event: Event) => {
      const detail = (event as CustomEvent<unknown>).detail
      if (!isValidToastDetail(detail)) {
        if (import.meta.env.DEV) {
          console.warn('[toast] ignored malformed payload', detail)
        }
        return
      }
      addNotification({ type: detail.type, message: detail.message, duration: detail.duration })
    }

    window.addEventListener(TOAST_EVENT, handleToastEvent)

    return () => {
      window.removeEventListener(TOAST_EVENT, handleToastEvent)
      delete window.__ecommerceToastHost
    }
  }, [addNotification])
}

export default useToastBridge
