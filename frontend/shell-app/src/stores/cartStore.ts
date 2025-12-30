import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { devtools } from 'zustand/middleware'
import type { CartState, CartItem } from './types'

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

// Selectors
export const selectCartItems = (state: CartState) => state.items
export const selectCartTotal = (state: CartState) => state.total
export const selectCartItemCount = (state: CartState) => state.itemCount
export const selectCartItem = (productId: string) => (state: CartState) =>
  state.items.find((item) => item.productId === productId)
export const selectIsInCart = (productId: string) => (state: CartState) =>
  state.items.some((item) => item.productId === productId)
