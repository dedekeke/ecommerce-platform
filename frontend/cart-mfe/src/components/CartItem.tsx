import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import Divider from '@mui/material/Divider'
import AddIcon from '@mui/icons-material/Add'
import RemoveIcon from '@mui/icons-material/Remove'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'
import type { CartItem as CartItemType } from '../stores/types'

const PLACEHOLDER_IMAGE = 'https://via.placeholder.com/80x80?text=Product'

interface CartItemProps {
  item: CartItemType
  onUpdateQty: (productId: string, qty: number) => void
  onRemove: (productId: string) => void
}

export default function CartItem({ item, onUpdateQty, onRemove }: CartItemProps) {
  const subtotal = (item.price * item.quantity).toFixed(2)

  return (
    <Box>
      <Stack
        direction="row"
        spacing={2}
        alignItems="center"
        sx={{ py: 2 }}
      >
        <Box
          component="img"
          src={item.image ?? PLACEHOLDER_IMAGE}
          alt={item.name}
          sx={{
            width: 80,
            height: 80,
            objectFit: 'cover',
            borderRadius: 2,
            flexShrink: 0,
            bgcolor: 'grey.100',
          }}
        />

        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography
            variant="body1"
            fontWeight={500}
            noWrap
            sx={{ mb: 0.5, color: 'text.primary' }}
          >
            {item.name}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            ${item.price.toFixed(2)}
          </Typography>
        </Box>

        <Stack direction="row" alignItems="center" spacing={0.5}>
          <IconButton
            size="small"
            onClick={() => onUpdateQty(item.productId, item.quantity - 1)}
            disabled={item.quantity <= 1}
            aria-label="Decrease quantity"
            sx={{
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              '&:hover': { bgcolor: 'action.hover' },
            }}
          >
            <RemoveIcon fontSize="small" />
          </IconButton>

          <Box
            component="input"
            type="number"
            value={item.quantity}
            readOnly
            aria-label="Quantity"
            sx={{
              width: 40,
              textAlign: 'center',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              py: 0.5,
              fontSize: '0.875rem',
              fontWeight: 500,
              color: 'text.primary',
              bgcolor: 'background.paper',
              '&:focus': { outline: '2px solid', outlineColor: 'primary.main' },
            }}
          />

          <IconButton
            size="small"
            onClick={() => onUpdateQty(item.productId, item.quantity + 1)}
            aria-label="Increase quantity"
            sx={{
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              '&:hover': { bgcolor: 'action.hover' },
            }}
          >
            <AddIcon fontSize="small" />
          </IconButton>
        </Stack>

        <Typography
          variant="body1"
          fontWeight={600}
          sx={{ minWidth: 64, textAlign: 'right', color: 'text.primary' }}
        >
          ${subtotal}
        </Typography>

        <IconButton
          size="small"
          onClick={() => onRemove(item.productId)}
          aria-label={`Remove ${item.name}`}
          sx={{
            color: 'text.secondary',
            '&:hover': { color: 'error.main', bgcolor: 'error.light' + '20' },
            transition: 'color 0.15s ease',
          }}
        >
          <DeleteOutlineIcon fontSize="small" />
        </IconButton>
      </Stack>
      <Divider />
    </Box>
  )
}
