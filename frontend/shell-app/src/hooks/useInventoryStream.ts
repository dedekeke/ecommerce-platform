import { useEffect, useRef } from 'react'
import { useInventoryStore } from '../stores/inventoryStore'

const STREAM_PATH = '/api/inventory/stream'
const MAX_BACKOFF_MS = 30_000

interface UseInventoryStreamOpts {
  productIds?: string[]
  baseUrl?: string
  enabled?: boolean
}

export function useInventoryStream({
  productIds,
  baseUrl = import.meta.env.VITE_API_BASE_URL ?? '',
  enabled = true,
}: UseInventoryStreamOpts = {}): void {
  const setUpdate = useInventoryStore((s) => s.setUpdate)
  const setError = useInventoryStore((s) => s.setError)
  const sourceRef = useRef<EventSource | null>(null)
  const backoffRef = useRef(1_000)

  useEffect(() => {
    if (!enabled || typeof EventSource === 'undefined') return

    let cancelled = false
    let timer: number | null = null

    const url = productIds && productIds.length > 0
      ? `${baseUrl}${STREAM_PATH}?productIds=${productIds.join(',')}`
      : `${baseUrl}${STREAM_PATH}`

    const open = () => {
      if (cancelled) return
      const es = new EventSource(url)
      sourceRef.current = es

      es.addEventListener('stock-update', (ev) => {
        try {
          const data = JSON.parse((ev as MessageEvent).data)
          setUpdate(data)
          backoffRef.current = 1_000
          setError(null)
        } catch (err) {
          setError(err as Error)
        }
      })

      es.onerror = () => {
        es.close()
        sourceRef.current = null
        if (cancelled) return
        const wait = Math.min(backoffRef.current, MAX_BACKOFF_MS)
        backoffRef.current = Math.min(backoffRef.current * 2, MAX_BACKOFF_MS)
        timer = window.setTimeout(open, wait)
      }
    }

    open()

    return () => {
      cancelled = true
      if (timer) window.clearTimeout(timer)
      sourceRef.current?.close()
      sourceRef.current = null
    }
  }, [enabled, baseUrl, productIds?.join(','), setUpdate, setError])
}
