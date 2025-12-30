import { useCallback } from 'react'
import { useNotificationStore } from '../stores'
import type { NotificationType } from '../stores'

export const useNotifications = () => {
  const addNotification = useNotificationStore((state) => state.addNotification)
  const removeNotification = useNotificationStore((state) => state.removeNotification)
  const clearNotifications = useNotificationStore((state) => state.clearNotifications)
  const notifications = useNotificationStore((state) => state.notifications)

  const showSuccess = useCallback(
    (message: string, duration?: number) => {
      addNotification({ type: 'success', message, duration })
    },
    [addNotification]
  )

  const showError = useCallback(
    (message: string, duration?: number) => {
      addNotification({ type: 'error', message, duration })
    },
    [addNotification]
  )

  const showWarning = useCallback(
    (message: string, duration?: number) => {
      addNotification({ type: 'warning', message, duration })
    },
    [addNotification]
  )

  const showInfo = useCallback(
    (message: string, duration?: number) => {
      addNotification({ type: 'info', message, duration })
    },
    [addNotification]
  )

  const show = useCallback(
    (type: NotificationType, message: string, duration?: number) => {
      addNotification({ type, message, duration })
    },
    [addNotification]
  )

  return {
    notifications,
    showSuccess,
    showError,
    showWarning,
    showInfo,
    show,
    remove: removeNotification,
    clear: clearNotifications,
  }
}
