import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useCheckout } from './useCheckout'
import { useCheckoutStore } from '../stores/checkoutStore'

const mockAddress = {
  fullName: 'Jane Doe',
  line1: '123 Main St',
  line2: '',
  city: 'San Francisco',
  state: 'CA',
  postalCode: '94105',
  country: 'US',
}

describe('useCheckout', () => {
  beforeEach(() => {
    useCheckoutStore.getState().reset()
  })

  it('should return initial step as 0', () => {
    const { result } = renderHook(() => useCheckout())
    expect(result.current.step).toBe(0)
  })

  it('should report isFirstStep true when on step 0', () => {
    const { result } = renderHook(() => useCheckout())
    expect(result.current.isFirstStep).toBe(true)
    expect(result.current.isLastStep).toBe(false)
  })

  it('should report isLastStep true when on step 2', () => {
    const { result } = renderHook(() => useCheckout())
    act(() => result.current.goToStep(2))
    expect(result.current.isLastStep).toBe(true)
    expect(result.current.isFirstStep).toBe(false)
  })

  it('should advance step when goNext is called', () => {
    const { result } = renderHook(() => useCheckout())
    act(() => result.current.goNext())
    expect(result.current.step).toBe(1)
  })

  it('should not advance beyond step 2', () => {
    const { result } = renderHook(() => useCheckout())
    act(() => result.current.goToStep(2))
    act(() => result.current.goNext())
    expect(result.current.step).toBe(2)
  })

  it('should go back when goBack is called', () => {
    const { result } = renderHook(() => useCheckout())
    act(() => result.current.goToStep(1))
    act(() => result.current.goBack())
    expect(result.current.step).toBe(0)
  })

  it('should not go below step 0', () => {
    const { result } = renderHook(() => useCheckout())
    act(() => result.current.goBack())
    expect(result.current.step).toBe(0)
  })

  it('should expose a non-empty idempotencyKey from store', () => {
    const { result } = renderHook(() => useCheckout())
    expect(result.current.idempotencyKey).toBeTruthy()
  })

  it('should expose address from store', () => {
    const { result } = renderHook(() => useCheckout())
    expect(result.current.address).toBeNull()

    act(() => result.current.setAddress(mockAddress))
    expect(result.current.address).toEqual(mockAddress)
  })

  it('should reset checkout state', () => {
    const { result } = renderHook(() => useCheckout())
    act(() => result.current.goToStep(2))
    act(() => result.current.setAddress(mockAddress))
    act(() => result.current.reset())
    expect(result.current.step).toBe(0)
    expect(result.current.address).toBeNull()
  })

  it('should compute canProceed false on step 0 when no address set', () => {
    const { result } = renderHook(() => useCheckout())
    expect(result.current.canProceed).toBe(false)
  })

  it('should compute canProceed true on step 0 when address is set', () => {
    const { result } = renderHook(() => useCheckout())
    act(() => result.current.setAddress(mockAddress))
    expect(result.current.canProceed).toBe(true)
  })

  it('should always allow canProceed on step 1 (review step submits the order)', () => {
    const { result } = renderHook(() => useCheckout())
    act(() => result.current.goToStep(1))
    expect(result.current.canProceed).toBe(true)
  })

  it('should compute canProceed false on step 2 (payment step has no generic action)', () => {
    const { result } = renderHook(() => useCheckout())
    act(() => result.current.goToStep(2))
    expect(result.current.canProceed).toBe(false)
  })
})
