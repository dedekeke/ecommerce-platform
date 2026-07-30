import { useEffect } from 'react'
import { useCartStore } from '../stores'

/**
 * Exposes `window.__cartBridge` so federated MFEs that cannot import the shell's zustand store
 * directly (e.g. the Angular user-dashboard-mfe) can add an item to the shell's cart. Mirrors
 * `useExposeAuthToken`'s install-on-mount / remove-on-unmount pattern. Writes are local-store
 * only (no cart-service POST) to match cart-mfe's own `addItem`, which is store-only today.
 */
export function useCartBridge(): void {
  const addItem = useCartStore((state) => state.addItem)

  useEffect(() => {
    const bridge = {
      addItem: ({ quantity = 1, ...item }: CartBridgeItem) => {
        for (let i = 0; i < quantity; i += 1) {
          addItem(item)
        }
      },
    }

    window.__cartBridge = bridge

    return () => {
      if (window.__cartBridge === bridge) {
        delete window.__cartBridge
      }
    }
  }, [addItem])
}

export default useCartBridge
