import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { devtools } from 'zustand/middleware'
import type { CartState, CartItem, AppliedPromotion } from './types'

const calculateTotal = (items: CartItem[]): number => {
  return items.reduce((sum, item) => sum + item.price * item.quantity, 0)
}

const calculateItemCount = (items: CartItem[]): number => {
  return items.reduce((sum, item) => sum + item.quantity, 0)
}

const initialState = {
  items: [] as CartItem[],
  total: 0,
  itemCount: 0,
  promotionCode: null as string | null,
  discountAmount: null as number | null,
  promotionName: null as string | null,
}

export const useCartStore = create<CartState>()(
  devtools(
    persist(
      (set) => ({
        ...initialState,

        addItem: (item: Omit<CartItem, 'quantity'>) =>
          set(
            (state) => {
              const existingItem = state.items.find(
                (i) => i.productId === item.productId
              )

              let newItems: CartItem[]

              if (existingItem) {
                newItems = state.items.map((i) =>
                  i.productId === item.productId
                    ? { ...i, quantity: i.quantity + 1 }
                    : i
                )
              } else {
                newItems = [...state.items, { ...item, quantity: 1 }]
              }

              return {
                items: newItems,
                total: calculateTotal(newItems),
                itemCount: calculateItemCount(newItems),
              }
            },
            false,
            'addItem'
          ),

        removeItem: (productId: string) =>
          set(
            (state) => {
              const newItems = state.items.filter(
                (item) => item.productId !== productId
              )

              return {
                items: newItems,
                total: calculateTotal(newItems),
                itemCount: calculateItemCount(newItems),
              }
            },
            false,
            'removeItem'
          ),

        updateQuantity: (productId: string, quantity: number) =>
          set(
            (state) => {
              if (quantity <= 0) {
                const newItems = state.items.filter(
                  (item) => item.productId !== productId
                )
                return {
                  items: newItems,
                  total: calculateTotal(newItems),
                  itemCount: calculateItemCount(newItems),
                }
              }

              const newItems = state.items.map((item) =>
                item.productId === productId ? { ...item, quantity } : item
              )

              return {
                items: newItems,
                total: calculateTotal(newItems),
                itemCount: calculateItemCount(newItems),
              }
            },
            false,
            'updateQuantity'
          ),

        clearCart: () =>
          set(
            {
              ...initialState,
            },
            false,
            'clearCart'
          ),

        applyPromotion: (promotion: AppliedPromotion) =>
          set(
            {
              promotionCode: promotion.code,
              discountAmount: promotion.discountAmount,
              promotionName: promotion.promotionName,
            },
            false,
            'applyPromotion'
          ),

        removePromotion: () =>
          set(
            { promotionCode: null, discountAmount: null, promotionName: null },
            false,
            'removePromotion'
          ),
      }),
      {
        name: 'cart-storage',
        storage: createJSONStorage(() => localStorage),
        version: 1,
        // v0 payloads (pre-promotion fields) lack promotionCode/discountAmount/promotionName —
        // pass them through as-is; zustand's default merge fills the missing keys from
        // initialState so existing carts survive instead of being wiped.
        migrate: (persistedState) => persistedState as CartState,
        partialize: (state) => ({
          items: state.items,
          total: state.total,
          itemCount: state.itemCount,
          promotionCode: state.promotionCode,
          discountAmount: state.discountAmount,
          promotionName: state.promotionName,
        }),
      }
    ),
    { name: 'CartStore' }
  )
)

// Selectors
export const selectCartItems = (state: CartState) => state.items
export const selectCartTotal = (state: CartState) => state.total
export const selectCartItemCount = (state: CartState) => state.itemCount
export const selectCartItem = (productId: string) => (state: CartState) =>
  state.items.find((item) => item.productId === productId)
export const selectIsInCart = (productId: string) => (state: CartState) =>
  state.items.some((item) => item.productId === productId)
export const selectPromotionCode = (state: CartState) => state.promotionCode
export const selectDiscountAmount = (state: CartState) => state.discountAmount
export const selectPromotionName = (state: CartState) => state.promotionName
