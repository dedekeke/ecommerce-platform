import { useEffect, useMemo } from 'react'
import { useUserPreferencesStore, selectTheme } from '../stores/userPreferencesStore'
import type { ThemeMode } from '../stores/types'
import { lightTheme, darkTheme } from '../theme'

function getSystemPreference(): 'light' | 'dark' {
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function resolveMode(preference: ThemeMode): 'light' | 'dark' {
  if (preference === 'system') return getSystemPreference()
  return preference
}

export function useColorMode() {
  const preference = useUserPreferencesStore(selectTheme)
  const { setTheme } = useUserPreferencesStore.getState()

  const resolvedMode = useMemo(() => resolveMode(preference), [preference])
  const muiTheme = resolvedMode === 'dark' ? darkTheme : lightTheme

  useEffect(() => {
    if (preference !== 'system') return

    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = () => {
      // Trigger re-render by re-setting 'system' — the store is unchanged but
      // component that reads `resolvedMode` will recompute on next render.
      // We force it by touching a harmless setTheme('system') no-op.
      useUserPreferencesStore.setState({ theme: 'system' })
    }
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [preference])

  useEffect(() => {
    const html = document.documentElement
    html.classList.remove('light', 'dark')
    html.classList.add(resolvedMode)
  }, [resolvedMode])

  const toggle = () => {
    const next: ThemeMode = resolvedMode === 'dark' ? 'light' : 'dark'
    setTheme(next)
  }

  return { resolvedMode, muiTheme, preference, toggle, setTheme }
}
