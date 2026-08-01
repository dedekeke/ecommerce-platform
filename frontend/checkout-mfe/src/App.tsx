import { Routes, Route } from 'react-router-dom'
import CheckoutPage from './pages/CheckoutPage'
import ConfirmationPage from './pages/ConfirmationPage'

function App() {
  return (
    <Routes>
      <Route index element={<CheckoutPage />} />
      <Route path="confirmation/:orderId" element={<ConfirmationPage />} />
    </Routes>
  )
}

export default App
