import { useEffect } from 'react'
import { addItemWithServerSync } from '../stores/cartSync'

/**
 * Exposes `window.__cartBridge` so federated MFEs that cannot import the shell's zustand store
 * directly (e.g. the Angular user-dashboard-mfe) can add an item to the shell's cart. Mirrors
 * `useExposeAuthToken`'s install-on-mount / remove-on-unmount pattern. Adds are optimistic in
 * the local store and, for authenticated shoppers, written through to cart-service with
 * rollback + error toast on failure (see addItemWithServerSync).
 */
export function useCartBridge(): void {
  useEffect(() => {
    const bridge = {
      addItem: ({ quantity = 1, ...item }: CartBridgeItem) => {
        void addItemWithServerSync(item, quantity)
      },
    }

    window.__cartBridge = bridge

    return () => {
      if (window.__cartBridge === bridge) {
        delete window.__cartBridge
      }
    }
  }, [])
}

export default useCartBridge
