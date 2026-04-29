import { Routes, Route, Link } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { Typography, Box, Paper, Grid } from '@mui/material'
import { MainLayout } from './components/layout'
import { ProtectedRoute } from './components/auth'
import { PageSkeleton } from './components/common'
import { useCartStore, selectCartItemCount } from './stores'
import { MicroFrontendLoader, MFEErrorBoundary, useMFEPreload, type MFEName } from './mfe'

interface MFERouteProps {
  mfeName: MFEName
  protected?: boolean
}

function MFERoute({ mfeName, protected: isProtected = false }: MFERouteProps) {
  const content = (
    <Box sx={{ width: '100%', px: { xs: 2, md: 4 } }}>
      <MFEErrorBoundary mfeName={mfeName}>
        <MicroFrontendLoader mfeName={mfeName} />
      </MFEErrorBoundary>
    </Box>
  )

  if (isProtected) {
    return <ProtectedRoute>{content}</ProtectedRoute>
  }

  return content
}

interface PreloadLinkProps {
  to: string
  mfeName: MFEName
  children: React.ReactNode
}

function PreloadLink({ to, mfeName, children }: PreloadLinkProps) {
  const { onMouseEnter, onMouseLeave, onFocus, onBlur } = useMFEPreload(mfeName)

  return (
    <Link
      to={to}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onFocus={onFocus}
      onBlur={onBlur}
      style={{ textDecoration: 'none', color: 'inherit' }}
    >
      {children}
    </Link>
  )
}

function Home() {
  const categories = [
    { name: 'Electronics', path: '/products?category=electronics' },
    { name: 'Fashion', path: '/products?category=fashion' },
    { name: 'Home & Garden', path: '/products?category=home-garden' },
  ]

  return (
    <Box sx={{ width: '100%', px: { xs: 2, md: 4 } }}>
      <Box sx={{ textAlign: 'center', py: 8 }}>
        <Typography variant="h2" component="h1" gutterBottom fontWeight={700}>
          Welcome to E-Commerce
        </Typography>
        <Typography variant="h5" color="text.secondary" sx={{ mb: 4 }}>
          Discover amazing products at great prices
        </Typography>
        <Grid container spacing={3} sx={{ mt: 4 }}>
          {categories.map((category) => (
            <Grid size={{ xs: 12, md: 4 }} key={category.name}>
              <PreloadLink to={category.path} mfeName="productCatalog">
                <Paper
                  sx={{
                    p: 4,
                    textAlign: 'center',
                    cursor: 'pointer',
                    transition: 'transform 0.2s, box-shadow 0.2s',
                    '&:hover': {
                      transform: 'translateY(-4px)',
                      boxShadow: 4,
                    },
                  }}
                >
                  <Typography variant="h6">{category.name}</Typography>
                </Paper>
              </PreloadLink>
            </Grid>
          ))}
        </Grid>
      </Box>
    </Box>
  )
}

function App() {
  const { isLoading } = useAuth0()
  const cartItemCount = useCartStore(selectCartItemCount)

  return (
    <MainLayout cartItemCount={cartItemCount}>
      {isLoading ? (
        <Box sx={{ width: '100%', px: { xs: 2, md: 4 } }}>
          <PageSkeleton />
        </Box>
      ) : (
        <Routes>
          <Route path="/" element={<Home />} />
          <Route
            path="/products/*"
            element={<MFERoute mfeName="productCatalog" />}
          />
          <Route
            path="/categories/*"
            element={<MFERoute mfeName="productCatalog" />}
          />
          <Route path="/cart" element={<MFERoute mfeName="cart" />} />
          <Route
            path="/checkout/*"
            element={<MFERoute mfeName="checkout" protected />}
          />
          <Route
            path="/profile/*"
            element={<MFERoute mfeName="userDashboard" protected />}
          />
          <Route
            path="/orders/*"
            element={<MFERoute mfeName="userDashboard" protected />}
          />
          <Route
            path="/admin/*"
            element={<MFERoute mfeName="adminDashboard" protected />}
          />
        </Routes>
      )}
    </MainLayout>
  )
}

export default App
