import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useNotificationStore } from './notificationStore'

describe('notificationStore', () => {
  beforeEach(() => {
    useNotificationStore.getState().clearNotifications()
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have empty notifications array', () => {
      const { notifications } = useNotificationStore.getState()
      expect(notifications).toEqual([])
    })
  })

  describe('addNotification', () => {
    it('should add a notification', () => {
      useNotificationStore.getState().addNotification({
        type: 'success',
        message: 'Test message',
      })

      const { notifications } = useNotificationStore.getState()
      expect(notifications).toHaveLength(1)
      expect(notifications[0].type).toBe('success')
      expect(notifications[0].message).toBe('Test message')
    })

    it('should generate unique id for each notification', () => {
      useNotificationStore.getState().addNotification({
        type: 'success',
        message: 'First message',
      })
      useNotificationStore.getState().addNotification({
        type: 'error',
        message: 'Second message',
      })

      const { notifications } = useNotificationStore.getState()
      expect(notifications[0].id).not.toBe(notifications[1].id)
    })

    it('should support different notification types', () => {
      useNotificationStore.getState().addNotification({ type: 'success', message: 'Success' })
      useNotificationStore.getState().addNotification({ type: 'error', message: 'Error' })
      useNotificationStore.getState().addNotification({ type: 'warning', message: 'Warning' })
      useNotificationStore.getState().addNotification({ type: 'info', message: 'Info' })

      const { notifications } = useNotificationStore.getState()
      expect(notifications).toHaveLength(4)
      expect(notifications.map(n => n.type)).toEqual(['success', 'error', 'warning', 'info'])
    })

    it('should support custom duration', () => {
      useNotificationStore.getState().addNotification({
        type: 'info',
        message: 'Custom duration',
        duration: 10000,
      })

      const { notifications } = useNotificationStore.getState()
      expect(notifications[0].duration).toBe(10000)
    })
  })

  describe('removeNotification', () => {
    it('should remove notification by id', () => {
      useNotificationStore.getState().addNotification({
        type: 'success',
        message: 'Test message',
      })

      const { notifications } = useNotificationStore.getState()
      const notificationId = notifications[0].id

      useNotificationStore.getState().removeNotification(notificationId)

      const updatedState = useNotificationStore.getState()
      expect(updatedState.notifications).toHaveLength(0)
    })

    it('should only remove the specified notification', () => {
      useNotificationStore.getState().addNotification({
        type: 'success',
        message: 'First',
      })
      useNotificationStore.getState().addNotification({
        type: 'error',
        message: 'Second',
      })

      const { notifications } = useNotificationStore.getState()
      const firstId = notifications[0].id

      useNotificationStore.getState().removeNotification(firstId)

      const updatedState = useNotificationStore.getState()
      expect(updatedState.notifications).toHaveLength(1)
      expect(updatedState.notifications[0].message).toBe('Second')
    })

    it('should do nothing if id not found', () => {
      useNotificationStore.getState().addNotification({
        type: 'success',
        message: 'Test',
      })

      useNotificationStore.getState().removeNotification('non-existent-id')

      const { notifications } = useNotificationStore.getState()
      expect(notifications).toHaveLength(1)
    })
  })

  describe('clearNotifications', () => {
    it('should remove all notifications', () => {
      useNotificationStore.getState().addNotification({ type: 'success', message: 'First' })
      useNotificationStore.getState().addNotification({ type: 'error', message: 'Second' })
      useNotificationStore.getState().addNotification({ type: 'info', message: 'Third' })

      useNotificationStore.getState().clearNotifications()

      const { notifications } = useNotificationStore.getState()
      expect(notifications).toHaveLength(0)
    })
  })
})
