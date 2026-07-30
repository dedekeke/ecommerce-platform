import { useCallback, useEffect, useRef } from 'react'
import { Alert, Box, IconButton, useTheme } from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import { motion, AnimatePresence } from 'framer-motion'
import { useNotificationStore, selectNotifications } from '../../stores'
import type { Notification } from '../../stores'
import { designTokens } from '../../theme'

const MAX_VISIBLE_TOASTS = 5
const DEFAULT_DURATION = 5000
const ERROR_DURATION = 7000
const MESSAGE_MAX_LENGTH = 200

function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined') return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

// Defense-in-depth: truncate regardless of what an MFE sends, so a runaway/malicious payload
// can never blow up the fixed-width toast UI.
function truncateMessage(message: string): string {
  if (message.length <= MESSAGE_MAX_LENGTH) return message
  return `${message.slice(0, MESSAGE_MAX_LENGTH)}…`
}

function getDuration(notification: Notification): number {
  if (notification.duration !== undefined) return notification.duration
  return notification.type === 'error' ? ERROR_DURATION : DEFAULT_DURATION
}

const variants = {
  initial: { opacity: 0, x: 32, scale: 0.98 },
  animate: { opacity: 1, x: 0, scale: 1 },
  exit: { opacity: 0, x: 32, scale: 0.98 },
}

const reducedVariants = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
}

interface ToastProps {
  notification: Notification
  onClose: (id: string) => void
}

function Toast({ notification, onClose }: ToastProps) {
  const duration = getDuration(notification)
  const remainingRef = useRef(duration)
  // 0 placeholder: always set by startTimer in the mount effect before any pause reads it
  const startedAtRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearTimer = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current)
  }, [])

  const startTimer = useCallback(
    (ms: number) => {
      startedAtRef.current = Date.now()
      timerRef.current = setTimeout(() => onClose(notification.id), ms)
    },
    [notification.id, onClose]
  )

  useEffect(() => {
    startTimer(remainingRef.current)
    return clearTimer
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once per toast instance
  }, [])

  const handlePause = () => {
    clearTimer()
    remainingRef.current -= Date.now() - startedAtRef.current
  }

  const handleResume = () => {
    startTimer(Math.max(remainingRef.current, 0))
  }

  const isReduced = prefersReducedMotion()

  return (
    <motion.div
      layout
      variants={isReduced ? reducedVariants : variants}
      initial="initial"
      animate="animate"
      exit="exit"
      transition={{ duration: isReduced ? 0.1 : 0.25, ease: [0.16, 1, 0.3, 1] }}
      onMouseEnter={handlePause}
      onMouseLeave={handleResume}
      style={{ pointerEvents: 'auto' }}
    >
      <Alert
        severity={notification.type}
        role={notification.type === 'error' ? 'alert' : 'status'}
        variant="filled"
        sx={{
          borderRadius: designTokens.radius.md,
          boxShadow: designTokens.shadows.lg,
          alignItems: 'center',
        }}
        action={
          <IconButton
            size="small"
            color="inherit"
            aria-label={`Dismiss ${notification.type} notification`}
            onClick={() => onClose(notification.id)}
          >
            <CloseIcon fontSize="small" />
          </IconButton>
        }
      >
        {truncateMessage(notification.message)}
      </Alert>
    </motion.div>
  )
}

export function ToastContainer() {
  const notifications = useNotificationStore(selectNotifications)
  const removeNotification = useNotificationStore((state) => state.removeNotification)
  const theme = useTheme()

  const visibleNotifications = notifications.slice(-MAX_VISIBLE_TOASTS)

  return (
    <Box
      sx={{
        position: 'fixed',
        top: { xs: 72, sm: 88 },
        right: { xs: 8, sm: 24 },
        left: { xs: 8, sm: 'auto' },
        width: { xs: 'auto', sm: 380 },
        zIndex: theme.zIndex.snackbar,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        pointerEvents: 'none',
      }}
    >
      <AnimatePresence initial={false}>
        {visibleNotifications.map((notification) => (
          <Toast key={notification.id} notification={notification} onClose={removeNotification} />
        ))}
      </AnimatePresence>
    </Box>
  )
}

export default ToastContainer
