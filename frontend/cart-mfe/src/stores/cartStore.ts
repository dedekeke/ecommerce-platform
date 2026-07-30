/**
 * Shared cart store for cart-mfe.
 *
 * Persistence key MUST match shell-app (`cart-storage`) so all MFEs and the
 * shell rehydrate from the same localStorage entry.  When another MFE or the
 * shell writes to `cart-storage` in the same browser tab, the `storage` event
 * does NOT fire (it only fires across tabs).  Cross-tab sync is therefore
 * covered automatically; same-tab sync relies on the shared Zustand singleton
 * that `@originjs/vite-plugin-federation` wires when `zustand` is listed in
 * the federation `shared` array with `singleton: true` semantics.
 */
import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { devtools } from 'zustand/middleware'
import { toast } from '../lib/toast'
import type { CartState, CartItem } from './types'

const calculateTotal = (items: CartItem[]): number =>
  items.reduce((sum, item) => sum + item.price * item.quantity, 0)

const calculateItemCount = (items: CartItem[]): number =>
  items.reduce((sum, item) => sum + item.quantity, 0)

const initialState = {
  items: [] as CartItem[],
  total: 0,
  itemCount: 0,
}

export const useCartStore = create<CartState>()(
  devtools(
    persist(
      (set) => ({
        ...initialState,

        addItem: (item: Omit<CartItem, 'quantity'>) => {
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
          )
          toast.success('Added to cart')
        },

        removeItem: (productId: string) => {
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
          )
          toast.success('Item removed from cart')
        },

        updateQuantity: (productId: string, quantity: number) => {
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
          )
          toast.success(quantity <= 0 ? 'Item removed from cart' : 'Quantity updated')
        },

        clearCart: () => {
          set({ ...initialState }, false, 'clearCart')
          toast.success('Cart cleared')
        },
      }),
      {
        name: 'cart-storage',
        storage: createJSONStorage(() => localStorage),
        partialize: (state) => ({
          items: state.items,
          total: state.total,
          itemCount: state.itemCount,
        }),
      }
    ),
    { name: 'CartStore' }
  )
)

export const selectCartItems = (state: CartState) => state.items
export const selectCartTotal = (state: CartState) => state.total
export const selectCartItemCount = (state: CartState) => state.itemCount

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === 'cart-storage') {
      useCartStore.persist.rehydrate()
    }
  })
}
