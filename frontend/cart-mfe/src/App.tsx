import { Container, Typography, Box } from '@mui/material'
import CartPage from './pages/CartPage'

function App() {
  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 4 }}>
        <Typography variant="h4" gutterBottom>
          Cart MFE (Standalone Dev Mode)
        </Typography>
        <CartPage />
      </Box>
    </Container>
  )
}

export default App
