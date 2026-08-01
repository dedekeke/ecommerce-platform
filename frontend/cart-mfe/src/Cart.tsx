import { Routes, Route } from 'react-router-dom'
import CartPage from './pages/CartPage'

/**
 * Federated entry point for the cart MFE.
 * ThemeProvider is intentionally omitted here — the shell-app provides it.
 * When running standalone (via main.tsx), the ThemeProvider in main.tsx wraps this.
 */
export default function Cart() {
  return (
    <Routes>
      <Route index element={<CartPage />} />
    </Routes>
  )
}
