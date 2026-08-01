import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act, fireEvent, within } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { ToastContainer } from './ToastContainer'
import { useNotificationStore } from '../../stores'
import type { Notification } from '../../stores'
import theme from '../../theme'

vi.mock('framer-motion', () => ({
  motion: {
    div: ({
      children,
      onMouseEnter,
      onMouseLeave,
      style,
    }: {
      children?: React.ReactNode
      onMouseEnter?: () => void
      onMouseLeave?: () => void
      style?: React.CSSProperties
    }) => (
      <div onMouseEnter={onMouseEnter} onMouseLeave={onMouseLeave} style={style}>
        {children}
      </div>
    ),
  },
  AnimatePresence: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
}))

const renderToastContainer = () =>
  render(
    <ThemeProvider theme={theme}>
      <ToastContainer />
    </ThemeProvider>
  )

const addNotification = (notification: Omit<Notification, 'id'>) => {
  act(() => {
    useNotificationStore.getState().addNotification(notification)
  })
}

describe('ToastContainer', () => {
  beforeEach(() => {
    useNotificationStore.setState({ notifications: [] })
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should not give the outer container its own live-region role (nested live regions are unreliable)', () => {
    const { container } = renderToastContainer()
    const outer = container.firstChild as HTMLElement
    expect(outer.getAttribute('role')).toBeNull()
    expect(outer.getAttribute('aria-live')).toBeNull()
  })

  it('should render nothing visible when there are no notifications', () => {
    renderToastContainer()
    expect(screen.queryAllByRole('alert')).toHaveLength(0)
  })

  it('should render a toast for each notification', () => {
    renderToastContainer()
    addNotification({ type: 'success', message: 'Item added to cart' })
    addNotification({ type: 'warning', message: 'Low stock' })
    expect(screen.getByText('Item added to cart')).toBeInTheDocument()
    expect(screen.getByText('Low stock')).toBeInTheDocument()
  })

  it('should give error toasts role="alert" and other toasts role="status"', () => {
    renderToastContainer()
    addNotification({ type: 'error', message: 'Payment failed' })
    addNotification({ type: 'info', message: 'FYI' })

    const alert = screen.getByRole('alert')
    expect(within(alert).getByText('Payment failed')).toBeInTheDocument()

    const statusRegions = screen.getAllByRole('status')
    const infoToast = statusRegions.find((el) => el.textContent?.includes('FYI'))
    expect(infoToast).toBeDefined()
  })

  it('should cap visible toasts at 5, keeping the most recent', () => {
    renderToastContainer()
    for (let i = 1; i <= 7; i += 1) {
      addNotification({ type: 'info', message: `Toast ${i}` })
    }
    expect(screen.queryByText('Toast 1')).not.toBeInTheDocument()
    expect(screen.queryByText('Toast 2')).not.toBeInTheDocument()
    expect(screen.getByText('Toast 3')).toBeInTheDocument()
    expect(screen.getByText('Toast 7')).toBeInTheDocument()
  })

  it('should have a close button with an accessible label', () => {
    renderToastContainer()
    addNotification({ type: 'success', message: 'Saved' })
    expect(screen.getByRole('button', { name: /dismiss success notification/i })).toBeInTheDocument()
  })

  it('should remove the notification when the close button is clicked', () => {
    renderToastContainer()
    addNotification({ type: 'success', message: 'Saved' })
    fireEvent.click(screen.getByRole('button', { name: /dismiss success notification/i }))
    expect(screen.queryByText('Saved')).not.toBeInTheDocument()
    expect(useNotificationStore.getState().notifications).toHaveLength(0)
  })

  it('should auto-dismiss a default toast after 5000ms', () => {
    renderToastContainer()
    addNotification({ type: 'success', message: 'Saved' })
    expect(screen.getByText('Saved')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(4999)
    })
    expect(screen.getByText('Saved')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(screen.queryByText('Saved')).not.toBeInTheDocument()
  })

  it('should auto-dismiss an error toast after 7000ms by default', () => {
    renderToastContainer()
    addNotification({ type: 'error', message: 'Payment failed' })

    act(() => {
      vi.advanceTimersByTime(6999)
    })
    expect(screen.getByText('Payment failed')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(screen.queryByText('Payment failed')).not.toBeInTheDocument()
  })

  it('should respect an explicit duration override', () => {
    renderToastContainer()
    addNotification({ type: 'success', message: 'Quick', duration: 1000 })

    act(() => {
      vi.advanceTimersByTime(1000)
    })
    expect(screen.queryByText('Quick')).not.toBeInTheDocument()
  })

  it('should pause the auto-dismiss timer while hovered and resume on mouse leave', () => {
    renderToastContainer()
    addNotification({ type: 'success', message: 'Saved' })

    act(() => {
      vi.advanceTimersByTime(4000)
    })

    fireEvent.mouseEnter(screen.getByText('Saved').closest('.MuiAlert-root') as HTMLElement)

    act(() => {
      vi.advanceTimersByTime(5000)
    })
    expect(screen.getByText('Saved')).toBeInTheDocument()

    fireEvent.mouseLeave(screen.getByText('Saved').closest('.MuiAlert-root') as HTMLElement)

    act(() => {
      vi.advanceTimersByTime(999)
    })
    expect(screen.getByText('Saved')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(screen.queryByText('Saved')).not.toBeInTheDocument()
  })

  it('should clean up timers on unmount without throwing', () => {
    const { unmount } = renderToastContainer()
    addNotification({ type: 'success', message: 'Saved' })
    expect(() => unmount()).not.toThrow()
  })

  it('should truncate a message longer than 200 characters with an ellipsis', () => {
    renderToastContainer()
    const longMessage = 'a'.repeat(250)
    addNotification({ type: 'info', message: longMessage })

    expect(screen.queryByText(longMessage)).not.toBeInTheDocument()
    expect(screen.getByText(`${'a'.repeat(200)}…`)).toBeInTheDocument()
  })

  it('should render a message of exactly 200 characters without truncation', () => {
    renderToastContainer()
    const exactMessage = 'b'.repeat(200)
    addNotification({ type: 'info', message: exactMessage })

    expect(screen.getByText(exactMessage)).toBeInTheDocument()
  })
})
