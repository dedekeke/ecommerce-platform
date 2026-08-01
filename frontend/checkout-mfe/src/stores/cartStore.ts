/**
 * Shared cart store for checkout-mfe.
 *
 * Persistence key MUST match shell-app (`cart-storage`) so all MFEs and the
 * shell rehydrate from the same localStorage entry.  Cross-tab sync fires via
 * the storage listener below.  Same-tab singleton sharing relies on zustand
 * being in the federation shared array.
 */
import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { devtools } from 'zustand/middleware'

export interface CartItem {
  productId: string
  name: string
  price: number
  quantity: number
  image?: string
}

export interface CartState {
  items: CartItem[]
  total: number
  itemCount: number
  promotionCode: string | null
  discountAmount: number | null
  promotionName: string | null
  addItem: (item: Omit<CartItem, 'quantity'>) => void
  removeItem: (productId: string) => void
  updateQuantity: (productId: string, quantity: number) => void
  clearCart: () => void
}

const calculateTotal = (items: CartItem[]): number =>
  items.reduce((sum, item) => sum + item.price * item.quantity, 0)

const calculateItemCount = (items: CartItem[]): number =>
  items.reduce((sum, item) => sum + item.quantity, 0)

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
              const existing = state.items.find((i) => i.productId === item.productId)
              const newItems: CartItem[] = existing
                ? state.items.map((i) =>
                    i.productId === item.productId ? { ...i, quantity: i.quantity + 1 } : i
                  )
                : [...state.items, { ...item, quantity: 1 }]
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
              const newItems = state.items.filter((i) => i.productId !== productId)
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
                const newItems = state.items.filter((i) => i.productId !== productId)
                return {
                  items: newItems,
                  total: calculateTotal(newItems),
                  itemCount: calculateItemCount(newItems),
                }
              }
              const newItems = state.items.map((i) =>
                i.productId === productId ? { ...i, quantity } : i
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

        clearCart: () => set({ ...initialState }, false, 'clearCart'),
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

export const selectCartItems = (state: CartState) => state.items
export const selectCartTotal = (state: CartState) => state.total
export const selectCartItemCount = (state: CartState) => state.itemCount
export const selectPromotionCode = (state: CartState) => state.promotionCode
export const selectDiscountAmount = (state: CartState) => state.discountAmount
export const selectPromotionName = (state: CartState) => state.promotionName

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === 'cart-storage') {
      useCartStore.persist.rehydrate()
    }
  })
}
