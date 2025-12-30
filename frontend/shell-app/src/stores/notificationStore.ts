import { create } from 'zustand'
import { devtools } from 'zustand/middleware'
import type { NotificationState, Notification } from './types'

const generateId = (): string => {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`
}

const initialState = {
  notifications: [] as Notification[],
}

export const useNotificationStore = create<NotificationState>()(
  devtools(
    (set) => ({
      ...initialState,

      addNotification: (notification: Omit<Notification, 'id'>) =>
        set(
          (state) => ({
            notifications: [
              ...state.notifications,
              {
                id: generateId(),
                ...notification,
              },
            ],
          }),
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
