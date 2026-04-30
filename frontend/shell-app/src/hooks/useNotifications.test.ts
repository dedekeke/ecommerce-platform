import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useNotifications } from './useNotifications'
import { useNotificationStore } from '../stores'

beforeEach(() => {
  useNotificationStore.setState({ notifications: [] })
})

describe('useNotifications', () => {
  it('should start with an empty notifications list', () => {
    const { result } = renderHook(() => useNotifications())
    expect(result.current.notifications).toHaveLength(0)
  })

  it('showSuccess adds a success notification', () => {
    const { result } = renderHook(() => useNotifications())
    act(() => {
      result.current.showSuccess('Saved successfully')
    })
    expect(result.current.notifications).toHaveLength(1)
    expect(result.current.notifications[0].type).toBe('success')
    expect(result.current.notifications[0].message).toBe('Saved successfully')
  })

  it('showError adds an error notification', () => {
    const { result } = renderHook(() => useNotifications())
    act(() => {
      result.current.showError('Something failed')
    })
    expect(result.current.notifications[0].type).toBe('error')
    expect(result.current.notifications[0].message).toBe('Something failed')
  })

  it('showWarning adds a warning notification', () => {
    const { result } = renderHook(() => useNotifications())
    act(() => {
      result.current.showWarning('Low stock')
    })
    expect(result.current.notifications[0].type).toBe('warning')
    expect(result.current.notifications[0].message).toBe('Low stock')
  })

  it('showInfo adds an info notification', () => {
    const { result } = renderHook(() => useNotifications())
    act(() => {
      result.current.showInfo('New feature available')
    })
    expect(result.current.notifications[0].type).toBe('info')
    expect(result.current.notifications[0].message).toBe('New feature available')
  })

  it('show adds a notification of the specified type', () => {
    const { result } = renderHook(() => useNotifications())
    act(() => {
      result.current.show('warning', 'Generic warning')
    })
    expect(result.current.notifications[0].type).toBe('warning')
    expect(result.current.notifications[0].message).toBe('Generic warning')
  })

  it('show passes optional duration', () => {
    const { result } = renderHook(() => useNotifications())
    act(() => {
      result.current.showSuccess('Quick toast', 2000)
    })
    expect(result.current.notifications[0].duration).toBe(2000)
  })

  it('remove deletes a notification by id', () => {
    const { result } = renderHook(() => useNotifications())
    act(() => {
      result.current.showSuccess('To be removed')
    })
    const id = result.current.notifications[0].id
    act(() => {
      result.current.remove(id)
    })
    expect(result.current.notifications).toHaveLength(0)
  })

  it('clear removes all notifications', () => {
    const { result } = renderHook(() => useNotifications())
    act(() => {
      result.current.showSuccess('First')
      result.current.showError('Second')
      result.current.showInfo('Third')
    })
    expect(result.current.notifications).toHaveLength(3)
    act(() => {
      result.current.clear()
    })
    expect(result.current.notifications).toHaveLength(0)
  })

  it('each notification gets a unique id', () => {
    const { result } = renderHook(() => useNotifications())
    act(() => {
      result.current.showSuccess('First')
      result.current.showSuccess('Second')
    })
    const ids = result.current.notifications.map((n) => n.id)
    expect(new Set(ids).size).toBe(2)
  })
})
