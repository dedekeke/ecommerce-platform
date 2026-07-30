import { describe, it, expect, beforeAll, beforeEach, afterAll, afterEach } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { handlers } from '../test/mocks'
import { mockValidDiscount, mockInvalidDiscount } from '../test/mocks/promotion'
import { useCartStore } from '../stores/cartStore'
import { usePromotion } from './usePromotion'

const API_BASE = 'http://localhost:8080/api'
const server = setupServer(...handlers)

function captureToasts() {
  const events: CustomEvent[] = []
  const listener = (e: Event) => events.push(e as CustomEvent)
  window.addEventListener('ecommerce:toast', listener)
  return { events, cleanup: () => window.removeEventListener('ecommerce:toast', listener) }
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

beforeEach(() => {
  useCartStore.setState({
    items: [],
    total: 0,
    itemCount: 0,
    promotionCode: null,
    discountAmount: null,
    promotionName: null,
  })
})

describe('usePromotion', () => {
  it('should start with no applied promotion and no error', () => {
    const { result } = renderHook(() => usePromotion(100))
    expect(result.current.promotionCode).toBeNull()
    expect(result.current.discountAmount).toBe(0)
    expect(result.current.error).toBeNull()
    expect(result.current.isApplying).toBe(false)
  })

  it('should apply a valid code, persist it in the cart store and toast success', async () => {
    window.__ecommerceToastHost = true
    const { events, cleanup } = captureToasts()
    const { result } = renderHook(() => usePromotion(100))

    await act(async () => {
      await result.current.applyPromotion('SAVE10')
    })

    expect(result.current.promotionCode).toBe('SAVE10')
    expect(result.current.discountAmount).toBe(10)
    expect(result.current.error).toBeNull()
    expect(useCartStore.getState().promotionCode).toBe('SAVE10')
    expect(events).toHaveLength(1)
    expect(events[0]?.detail).toMatchObject({ type: 'success', message: 'Promotion applied' })

    cleanup()
    delete window.__ecommerceToastHost
  })

  it('should set an inline error and NOT apply the promotion for an invalid code', async () => {
    window.__ecommerceToastHost = true
    const { events, cleanup } = captureToasts()
    const { result } = renderHook(() => usePromotion(100))

    await act(async () => {
      await result.current.applyPromotion('BADCODE')
    })

    expect(result.current.promotionCode).toBeNull()
    expect(result.current.error).toMatch(/not found or not valid/i)
    expect(useCartStore.getState().promotionCode).toBeNull()
    expect(events).toHaveLength(0)

    cleanup()
    delete window.__ecommerceToastHost
  })

  it('should set an inline error without toasting on a network/server failure', async () => {
    window.__ecommerceToastHost = true
    const { events, cleanup } = captureToasts()
    server.use(
      http.post(`${API_BASE}/promotions/validate`, () =>
        HttpResponse.json({ message: 'Promotion service is down' }, { status: 500 })
      )
    )
    const { result } = renderHook(() => usePromotion(100))

    await act(async () => {
      await result.current.applyPromotion('SAVE10')
    })

    expect(result.current.error).toBe('Promotion service is down')
    expect(events).toHaveLength(0)

    cleanup()
    delete window.__ecommerceToastHost
  })

  it('should fall back to a generic invalid message when the server sends none', async () => {
    server.use(
      http.post(`${API_BASE}/promotions/validate`, () =>
        HttpResponse.json({ valid: false, message: '' })
      )
    )
    const { result } = renderHook(() => usePromotion(100))

    await act(async () => {
      await result.current.applyPromotion('SAVE10')
    })

    expect(result.current.error).toBe('This promo code is not valid')
  })

  it('should fall back to a generic error message when a failure has no response body', async () => {
    server.use(
      http.post(`${API_BASE}/promotions/validate`, () => HttpResponse.error())
    )
    const { result } = renderHook(() => usePromotion(100))

    await act(async () => {
      await result.current.applyPromotion('SAVE10')
    })

    expect(result.current.error).toBe('Could not apply this promo code. Please try again.')
  })

  it('should require a non-empty code', async () => {
    const { result } = renderHook(() => usePromotion(100))

    await act(async () => {
      await result.current.applyPromotion('   ')
    })

    expect(result.current.error).toMatch(/enter a promo code/i)
  })

  it('should toggle isApplying while the request is in flight', async () => {
    const { result } = renderHook(() => usePromotion(100))

    let applyPromise!: Promise<void>
    act(() => {
      applyPromise = result.current.applyPromotion('SAVE10')
    })
    expect(result.current.isApplying).toBe(true)

    await act(async () => {
      await applyPromise
    })
    expect(result.current.isApplying).toBe(false)
  })

  it('should clear the applied promotion and any error on removePromotion', async () => {
    const { result } = renderHook(() => usePromotion(100))

    await act(async () => {
      await result.current.applyPromotion('SAVE10')
    })
    expect(result.current.promotionCode).toBe('SAVE10')

    act(() => {
      result.current.removePromotion()
    })

    expect(result.current.promotionCode).toBeNull()
    expect(result.current.discountAmount).toBe(0)
    expect(result.current.error).toBeNull()
  })

  it('should reflect an already-applied promotion from the cart store on mount', async () => {
    useCartStore
      .getState()
      .applyPromotion({ code: 'SAVE10', discountAmount: 10, promotionName: '10 Off Sale' })

    const { result } = renderHook(() => usePromotion(100))

    await waitFor(() => {
      expect(result.current.promotionCode).toBe('SAVE10')
      expect(result.current.discountAmount).toBe(10)
    })
  })

  describe('revalidation on subtotal change', () => {
    it('should re-validate with the new subtotal when the cart quantity changes and update the discount', async () => {
      server.use(
        http.post(`${API_BASE}/promotions/validate`, async ({ request }) => {
          const body = (await request.json()) as { purchaseAmount: number }
          return HttpResponse.json({
            valid: true,
            message: 'Promotion is valid',
            discountAmount: body.purchaseAmount >= 200 ? 20 : 10,
            finalAmount: null,
            promotionCode: 'SAVE10',
            promotionName: '10 Off Sale',
          })
        })
      )
      const { result, rerender } = renderHook(({ subtotal }) => usePromotion(subtotal), {
        initialProps: { subtotal: 100 },
      })

      await act(async () => {
        await result.current.applyPromotion('SAVE10')
      })
      expect(result.current.discountAmount).toBe(10)

      rerender({ subtotal: 200 })

      await waitFor(() => expect(result.current.discountAmount).toBe(20), { timeout: 2000 })
      expect(result.current.promotionCode).toBe('SAVE10')
      expect(result.current.revalidationNotice).toBeNull()
    })

    it('should clear the promotion and show a notice when the new subtotal drops below the minimum purchase', async () => {
      server.use(
        http.post(`${API_BASE}/promotions/validate`, () => HttpResponse.json(mockValidDiscount)),
      )
      const { result, rerender } = renderHook(({ subtotal }) => usePromotion(subtotal), {
        initialProps: { subtotal: 100 },
      })

      await act(async () => {
        await result.current.applyPromotion('SAVE10')
      })
      expect(result.current.promotionCode).toBe('SAVE10')

      server.use(
        http.post(`${API_BASE}/promotions/validate`, () => HttpResponse.json(mockInvalidDiscount)),
      )
      rerender({ subtotal: 5 })

      await waitFor(() => expect(result.current.promotionCode).toBeNull(), { timeout: 2000 })
      expect(result.current.discountAmount).toBe(0)
      expect(result.current.revalidationNotice).toBe('Promo code no longer applies')
      expect(useCartStore.getState().promotionCode).toBeNull()
    })

    it('should not re-validate when the subtotal changes and no promotion is applied', async () => {
      const { result, rerender } = renderHook(({ subtotal }) => usePromotion(subtotal), {
        initialProps: { subtotal: 100 },
      })

      rerender({ subtotal: 200 })
      await new Promise((resolve) => setTimeout(resolve, 600))

      expect(result.current.promotionCode).toBeNull()
    })

    it('should debounce repeated subtotal changes into a single revalidation call', async () => {
      let callCount = 0
      server.use(
        http.post(`${API_BASE}/promotions/validate`, () => {
          callCount += 1
          return HttpResponse.json(mockValidDiscount)
        })
      )
      const { result, rerender } = renderHook(({ subtotal }) => usePromotion(subtotal), {
        initialProps: { subtotal: 100 },
      })

      await act(async () => {
        await result.current.applyPromotion('SAVE10')
      })
      callCount = 0

      rerender({ subtotal: 110 })
      rerender({ subtotal: 120 })
      rerender({ subtotal: 130 })

      await waitFor(() => expect(callCount).toBe(1), { timeout: 2000 })
    })
  })
})
