import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useCart } from './useCart'
import { useCartStore } from '../stores/cartStore'

describe('useCart', () => {
  beforeEach(() => {
    useCartStore.setState({ items: [], total: 0, itemCount: 0 })
  })

  it('should return empty cart state initially', () => {
    const { result } = renderHook(() => useCart())
    expect(result.current.items).toHaveLength(0)
    expect(result.current.total).toBe(0)
    expect(result.current.itemCount).toBe(0)
  })

  it('should expose addItem action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
    })
    expect(result.current.items).toHaveLength(1)
    expect(result.current.total).toBe(10.0)
  })

  it('should expose removeItem action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
    })
    act(() => {
      result.current.removeItem('p1')
    })
    expect(result.current.items).toHaveLength(0)
  })

  it('should expose updateQuantity action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
    })
    act(() => {
      result.current.updateQuantity('p1', 3)
    })
    expect(result.current.items[0]?.quantity).toBe(3)
  })

  it('should expose clearCart action', () => {
    const { result } = renderHook(() => useCart())
    act(() => {
      result.current.addItem({ productId: 'p1', name: 'Widget', price: 10.0 })
      result.current.clearCart()
    })
    expect(result.current.items).toHaveLength(0)
  })
})
