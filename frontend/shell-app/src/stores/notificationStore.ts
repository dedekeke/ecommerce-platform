import { create } from 'zustand'
import { devtools } from 'zustand/middleware'
import type { NotificationState, Notification } from './types'

const generateId = (): string => {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`
}

const initialState = {
  notifications: [] as Notification[],
}

// Guards against duplicate/flooding toasts (e.g. a retried request or a chatty MFE):
// identical type+message within this window is deduped, and the list never exceeds this size.
const DEDUPE_WINDOW_MS = 2000
const MAX_NOTIFICATIONS = 20

export const useNotificationStore = create<NotificationState>()(
  devtools(
    (set) => ({
      ...initialState,

      addNotification: (notification: Omit<Notification, 'id'>) =>
        set(
          (state) => {
            const now = Date.now()
            const isDuplicate = state.notifications.some(
              (n) =>
                n.type === notification.type &&
                n.message === notification.message &&
                n.addedAt !== undefined &&
                now - n.addedAt < DEDUPE_WINDOW_MS
            )
            if (isDuplicate) return state

            const nextNotifications = [
              ...state.notifications,
              {
                ...notification,
                id: generateId(),
                addedAt: now,
              },
            ]

            return {
              notifications: nextNotifications.slice(-MAX_NOTIFICATIONS),
            }
          },
          false,
          'addNotification'
        ),

      removeNotification: (id: string) =>
        set(
          (state) => ({
            notifications: state.notifications.filter(
              (notification) => notification.id !== id
            ),
          }),
          false,
          'removeNotification'
        ),

      clearNotifications: () =>
        set(
          {
            ...initialState,
          },
          false,
          'clearNotifications'
        ),
    }),
    { name: 'NotificationStore' }
  )
)

// Selectors
export const selectNotifications = (state: NotificationState) =>
  state.notifications
export const selectNotificationById = (id: string) => (state: NotificationState) =>
  state.notifications.find((n) => n.id === id)
export const selectNotificationCount = (state: NotificationState) =>
  state.notifications.length
export const selectHasNotifications = (state: NotificationState) =>
  state.notifications.length > 0
