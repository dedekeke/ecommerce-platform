import { renderHook, act } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useInventoryStream } from './useInventoryStream'
import { useInventoryStore } from '../stores/inventoryStore'

class MockEventSource {
  static instances: MockEventSource[] = []
  url: string
  listeners: Record<string, ((ev: MessageEvent) => void)[]> = {}
  onerror: ((ev: Event) => void) | null = null
  closed = false

  constructor(url: string) {
    this.url = url
    MockEventSource.instances.push(this)
  }

  addEventListener(name: string, fn: (ev: MessageEvent) => void) {
    ;(this.listeners[name] ??= []).push(fn)
  }

  emit(name: string, data: unknown) {
    const ev = { data: typeof data === 'string' ? data : JSON.stringify(data) } as MessageEvent
    this.listeners[name]?.forEach((fn) => fn(ev))
  }

  triggerError() {
    this.onerror?.(new Event('error'))
  }

  close() {
    this.closed = true
  }
}

describe('useInventoryStream', () => {
  beforeEach(() => {
    MockEventSource.instances = []
    ;(globalThis as unknown as { EventSource: typeof MockEventSource }).EventSource = MockEventSource
    useInventoryStore.getState().clear()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('opens an EventSource and writes stock-update payloads to the store', () => {
    renderHook(() => useInventoryStream())

    expect(MockEventSource.instances).toHaveLength(1)
    const es = MockEventSource.instances[0]
    expect(es.url).toContain('/api/inventory/stream')

    act(() => {
      es.emit('stock-update', {
        productId: 'p1',
        sku: 'SKU-1',
        availableQty: 4,
        previousQty: 5,
        ts: '2026-04-30T12:00:00Z',
      })
    })

    const v = useInventoryStore.getState().byProductId['p1']
    expect(v?.availableQty).toBe(4)
  })

  it('appends productIds filter to the URL', () => {
    renderHook(() => useInventoryStream({ productIds: ['p1', 'p2'] }))
    expect(MockEventSource.instances[0].url).toContain('productIds=p1,p2')
  })

  it('reconnects with backoff on error', () => {
    renderHook(() => useInventoryStream())

    act(() => {
      MockEventSource.instances[0].triggerError()
    })

    expect(MockEventSource.instances[0].closed).toBe(true)

    act(() => {
      vi.advanceTimersByTime(1_500)
    })

    expect(MockEventSource.instances).toHaveLength(2)
  })

  it('closes the source on unmount', () => {
    const { unmount } = renderHook(() => useInventoryStream())
    unmount()
    expect(MockEventSource.instances[0].closed).toBe(true)
  })
})
