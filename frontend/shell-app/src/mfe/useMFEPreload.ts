import { useCallback, useRef, useState, useEffect } from 'react'
import type { MFEName, MFEPreloadOptions } from './types'
import { preloadModule, isModulePreloaded } from './moduleLoader'

interface UseMFEPreloadResult {
  onMouseEnter: () => void
  onMouseLeave: () => void
  onFocus: () => void
  onBlur: () => void
  preload: () => void
  isPreloading: boolean
  isPreloaded: boolean
}

const DEFAULT_DELAY = 150

export function useMFEPreload(
  mfeName: MFEName,
  options: MFEPreloadOptions = {}
): UseMFEPreloadResult {
  const { delay = DEFAULT_DELAY } = options
  const [isPreloaded, setIsPreloaded] = useState(() => isModulePreloaded(mfeName))
  const [isPreloading, setIsPreloading] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const hasPreloadedRef = useRef(isPreloaded)

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    }
  }, [])

  const doPreload = useCallback(() => {
    if (hasPreloadedRef.current) {
      return
    }

    hasPreloadedRef.current = true
    setIsPreloading(true)

    preloadModule(mfeName)
    setIsPreloaded(true)
    setIsPreloading(false)
  }, [mfeName])

  const startPreloadTimer = useCallback(() => {
    if (hasPreloadedRef.current) {
      return
    }

    timerRef.current = setTimeout(() => {
      doPreload()
    }, delay)
  }, [delay, doPreload])

  const cancelPreloadTimer = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const onMouseEnter = useCallback(() => {
    startPreloadTimer()
  }, [startPreloadTimer])

  const onMouseLeave = useCallback(() => {
    cancelPreloadTimer()
  }, [cancelPreloadTimer])

  const onFocus = useCallback(() => {
    startPreloadTimer()
  }, [startPreloadTimer])

  const onBlur = useCallback(() => {
    cancelPreloadTimer()
  }, [cancelPreloadTimer])

  const preload = useCallback(() => {
    cancelPreloadTimer()
    doPreload()
  }, [cancelPreloadTimer, doPreload])

  return {
    onMouseEnter,
    onMouseLeave,
    onFocus,
    onBlur,
    preload,
    isPreloading,
    isPreloaded,
  }
}
