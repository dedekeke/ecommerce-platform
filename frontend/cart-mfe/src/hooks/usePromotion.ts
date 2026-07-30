import { useState } from 'react'
import { isAxiosError } from 'axios'
import {
  useCartStore,
  selectPromotionCode,
  selectDiscountAmount,
} from '../stores/cartStore'
import { validatePromotion } from '../api/promotionService'
import { toast } from '../lib/toast'

const GENERIC_ERROR = 'Could not apply this promo code. Please try again.'

/**
 * Wires the promo code form to `POST /api/promotions/validate` and the cart store. Both the
 * "code not found" case (a normal 200 with `valid: false`) and transport/server failures surface
 * as the same inline `error` — never a toast, since PromoCodeInput renders its own field error
 * (see `validatePromotion`'s `skipErrorToast`). Only a successful apply toasts.
 */
export function usePromotion(subtotal: number) {
  const promotionCode = useCartStore(selectPromotionCode)
  const discountAmount = useCartStore(selectDiscountAmount) ?? 0
  const storeApplyPromotion = useCartStore((s) => s.applyPromotion)
  const storeRemovePromotion = useCartStore((s) => s.removePromotion)
  const [isApplying, setIsApplying] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const applyPromotion = async (rawCode: string) => {
    const code = rawCode.trim()
    if (!code) {
      setError('Enter a promo code')
      return
    }

    setIsApplying(true)
    setError(null)
    try {
      const result = await validatePromotion({ code, purchaseAmount: subtotal })
      if (!result.valid) {
        setError(result.message || 'This promo code is not valid')
        return
      }
      storeApplyPromotion({
        code: result.promotionCode ?? code,
        discountAmount: result.discountAmount ?? 0,
        promotionName: result.promotionName,
      })
      toast.success('Promotion applied')
    } catch (err) {
      const data = isAxiosError(err)
        ? (err.response?.data as { message?: string } | undefined)
        : undefined
      setError(data?.message ?? GENERIC_ERROR)
    } finally {
      setIsApplying(false)
    }
  }

  const removePromotion = () => {
    storeRemovePromotion()
    setError(null)
  }

  return { promotionCode, discountAmount, isApplying, error, applyPromotion, removePromotion }
}
