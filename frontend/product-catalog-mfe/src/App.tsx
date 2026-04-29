import { Container, Typography, Box } from '@mui/material'
import ProductCatalog from './ProductCatalog'

function App() {
  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 4 }}>
        <Typography variant="h4" gutterBottom>
          Product Catalog MFE (Standalone Dev Mode)
        </Typography>
        <ProductCatalog />
      </Box>
    </Container>
  )
}

export default App
