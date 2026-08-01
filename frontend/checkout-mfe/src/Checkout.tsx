import { Routes, Route } from 'react-router-dom'
import CheckoutPage from './pages/CheckoutPage'
import ConfirmationPage from './pages/ConfirmationPage'

/**
 * Federated entry point for the checkout MFE.
 * ThemeProvider is intentionally omitted here — the shell-app provides it.
 * When running standalone (via main.tsx), the ThemeProvider in main.tsx wraps this.
 */
export default function Checkout() {
  return (
    <Routes>
      <Route index element={<CheckoutPage />} />
      <Route path="confirmation/:orderId" element={<ConfirmationPage />} />
    </Routes>
  )
}
