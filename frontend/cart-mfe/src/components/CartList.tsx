import Box from '@mui/material/Box'
import CartItem from './CartItem'
import CartItemSkeleton from './CartItemSkeleton'
import EmptyCart from './EmptyCart'
import type { CartItem as CartItemType } from '../stores/types'

const SKELETON_COUNT = 3

interface CartListProps {
  items: CartItemType[]
  loading: boolean
  onUpdateQty: (productId: string, qty: number) => void
  onRemove: (productId: string) => void
}

export default function CartList({ items, loading, onUpdateQty, onRemove }: CartListProps) {
  if (loading) {
    return (
      <Box>
        {Array.from({ length: SKELETON_COUNT }).map((_, i) => (
          <CartItemSkeleton key={i} />
        ))}
      </Box>
    )
  }

  if (items.length === 0) {
    return <EmptyCart />
  }

  return (
    <Box>
      {items.map((item) => (
        <CartItem
          key={item.productId}
          item={item}
          onUpdateQty={onUpdateQty}
          onRemove={onRemove}
        />
      ))}
    </Box>
  )
}
