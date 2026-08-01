import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined'
import { Link } from 'react-router-dom'

export default function EmptyCart() {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        py: 10,
        textAlign: 'center',
      }}
    >
      <ShoppingCartOutlinedIcon
        sx={{ fontSize: 72, color: 'text.secondary', mb: 3, opacity: 0.4 }}
      />
      <Typography variant="h5" fontWeight={600} gutterBottom>
        Your cart is empty
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4, maxWidth: 320 }}>
        Looks like you haven't added anything yet. Explore our catalog to find something you'll love.
      </Typography>
      <Button
        component={Link}
        to="/products"
        variant="contained"
        size="large"
        href="/products"
        sx={{ px: 4 }}
      >
        Continue shopping
      </Button>
    </Box>
  )
}
