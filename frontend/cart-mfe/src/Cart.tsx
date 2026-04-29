import { ThemeProvider } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import { Routes, Route } from 'react-router-dom'
import { theme } from './theme/theme'
import CartPage from './pages/CartPage'

export default function Cart() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Routes>
        <Route index element={<CartPage />} />
      </Routes>
    </ThemeProvider>
  )
}
