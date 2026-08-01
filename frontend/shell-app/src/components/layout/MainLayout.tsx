import { useState, type ReactNode } from 'react'
import { Box, CssBaseline } from '@mui/material'
import { Header } from './Header'
import { Footer } from './Footer'
import { MobileDrawer } from './MobileDrawer'
import { ToastContainer } from '../common/ToastContainer'
import { useToastBridge } from '../../hooks/useToastBridge'

interface MainLayoutProps {
  children: ReactNode
  cartItemCount?: number
}

export const MainLayout = ({ children, cartItemCount = 0 }: MainLayoutProps) => {
  const [mobileOpen, setMobileOpen] = useState(false)
  useToastBridge()

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen)
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <CssBaseline />

      <Header cartItemCount={cartItemCount} onMenuClick={handleDrawerToggle} />

      <MobileDrawer open={mobileOpen} onClose={handleDrawerToggle} />

      <ToastContainer />

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          bgcolor: 'background.default',
          py: 3,
          px: { xs: 2, sm: 3 },
        }}
      >
        {children}
      </Box>

      <Footer />
    </Box>
  )
}
