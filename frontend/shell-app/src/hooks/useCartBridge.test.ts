import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useCartBridge } from './useCartBridge'
import { useCartStore } from '../stores'

describe('useCartBridge', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
    delete window.__cartBridge
  })

  afterEach(() => {
    delete window.__cartBridge
  })

  it('should install window.__cartBridge on mount', () => {
    renderHook(() => useCartBridge())
    expect(window.__cartBridge?.addItem).toBeTypeOf('function')
  })

  it('should remove window.__cartBridge on unmount', () => {
    const { unmount } = renderHook(() => useCartBridge())
    unmount()
    expect(window.__cartBridge).toBeUndefined()
  })

  it('should add the item to the shell cart store', () => {
    renderHook(() => useCartBridge())

    window.__cartBridge?.addItem({ productId: 'p1', name: 'Mouse', price: 9.99 })

    expect(useCartStore.getState().items).toEqual([
      { productId: 'p1', name: 'Mouse', price: 9.99, quantity: 1 },
    ])
    expect(useCartStore.getState().itemCount).toBe(1)
  })

  it('should add the item `quantity` times when quantity is greater than 1', () => {
    renderHook(() => useCartBridge())

    window.__cartBridge?.addItem({ productId: 'p1', name: 'Mouse', price: 9.99, quantity: 3 })

    expect(useCartStore.getState().itemCount).toBe(3)
    expect(useCartStore.getState().items).toEqual([
      { productId: 'p1', name: 'Mouse', price: 9.99, quantity: 3 },
    ])
  })

  it('should update the header badge count (itemCount) when items are added', () => {
    renderHook(() => useCartBridge())

    window.__cartBridge?.addItem({ productId: 'p1', name: 'Mouse', price: 9.99 })
    window.__cartBridge?.addItem({ productId: 'p2', name: 'Keyboard', price: 49.99 })

    expect(useCartStore.getState().itemCount).toBe(2)
  })
})
