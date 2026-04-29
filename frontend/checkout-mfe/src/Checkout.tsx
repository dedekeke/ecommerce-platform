import { ThemeProvider } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import { Routes, Route } from 'react-router-dom'
import { theme } from './theme/theme'
import CheckoutPage from './pages/CheckoutPage'
import ConfirmationPage from './pages/ConfirmationPage'

export default function Checkout() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Routes>
        <Route index element={<CheckoutPage />} />
        <Route path="confirmation/:orderId" element={<ConfirmationPage />} />
      </Routes>
    </ThemeProvider>
  )
}
