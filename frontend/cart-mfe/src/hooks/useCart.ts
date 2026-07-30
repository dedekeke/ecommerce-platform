import { useCartStore } from '../stores/cartStore'
import { toast } from '../lib/toast'

/**
 * Wires cart-store actions to UI-facing toasts. Toasts live here (the interaction layer), not in
 * the store, so they fire only on actual user actions and never on internal/no-op mutations.
 * `addItem` is intentionally untoasted here — adds originate from product-catalog-mfe, which
 * already toasts "Added to cart".
 */
export function useCart() {
  const items = useCartStore((s) => s.items)
  const total = useCartStore((s) => s.total)
  const itemCount = useCartStore((s) => s.itemCount)
  const addItem = useCartStore((s) => s.addItem)
  const storeRemoveItem = useCartStore((s) => s.removeItem)
  const storeUpdateQuantity = useCartStore((s) => s.updateQuantity)
  const storeClearCart = useCartStore((s) => s.clearCart)

  const removeItem = (productId: string) => {
    storeRemoveItem(productId)
    toast.success('Item removed from cart')
  }

  const updateQuantity = (productId: string, quantity: number) => {
    storeUpdateQuantity(productId, quantity)
    toast.success(quantity <= 0 ? 'Item removed from cart' : 'Quantity updated')
  }

  const clearCart = () => {
    storeClearCart()
    toast.success('Cart cleared')
  }

  return { items, total, itemCount, addItem, removeItem, updateQuantity, clearCart }
}
