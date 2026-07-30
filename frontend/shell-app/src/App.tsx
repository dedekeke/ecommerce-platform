import { Routes, Route, Link } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { useTranslation } from 'react-i18next'
import { Typography, Box, Paper, Grid } from '@mui/material'
import { MainLayout } from './components/layout'
import { ProtectedRoute, MFERouteGuard } from './components/auth'
import { PageSkeleton, NotFound, RouteProgressBar, PageTransition } from './components/common'
import { useCartStore, selectCartItemCount } from './stores'
import { MicroFrontendLoader, MFEErrorBoundary, useMFEPreload, type MFEName } from './mfe'
import { useExposeAuthToken, useCartBridge, useInventoryStream } from './hooks'
import { productService } from './api/services'
import { designTokens } from './theme'

interface MFERouteProps {
  mfeName: MFEName
  protected?: boolean
  componentProps?: Record<string, unknown>
}

function MFERoute({ mfeName, protected: isProtected = false, componentProps }: MFERouteProps) {
  const content = (
    <Box sx={{ width: '100%', px: { xs: 2, md: 4 } }}>
      <MFEErrorBoundary mfeName={mfeName}>
        <MicroFrontendLoader mfeName={mfeName} componentProps={componentProps} />
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
  const { t } = useTranslation()
  const categories = [
    { key: 'categoryElectronics', path: '/products?category=electronics', emoji: '💻' },
    { key: 'categoryFashion', path: '/products?category=fashion', emoji: '👗' },
    { key: 'categoryHomeGarden', path: '/products?category=home-garden', emoji: '🏡' },
  ] as const

  return (
    <Box sx={{ width: '100%', px: { xs: 2, md: 4 } }}>
      <Box sx={{ textAlign: 'center', py: { xs: 6, md: 10 } }}>
        <Typography
          variant="h2"
          component="h1"
          gutterBottom
          fontWeight={700}
          sx={{
            letterSpacing: '-0.02em',
            background: designTokens.gradients.hero,
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            backgroundClip: 'text',
          }}
        >
          {t('home.title')}
        </Typography>
        <Typography variant="h5" color="text.secondary" sx={{ mb: 6 }}>
          {t('home.subtitle')}
        </Typography>
        <Grid container spacing={3} sx={{ mt: 2 }}>
          {categories.map((category) => (
            <Grid size={{ xs: 12, md: 4 }} key={category.key}>
              <PreloadLink to={category.path} mfeName="productCatalog">
                <Paper
                  elevation={0}
                  sx={{
                    p: 5,
                    textAlign: 'center',
                    cursor: 'pointer',
                    border: '1px solid',
                    borderColor: 'divider',
                    transition: `all ${designTokens.duration.normal} ${designTokens.easing.out}`,
                    '&:hover': {
                      transform: 'translateY(-4px)',
                      boxShadow: designTokens.shadows.cardHover,
                      borderColor: 'primary.main',
                    },
                    '@media (prefers-reduced-motion: reduce)': {
                      '&:hover': { transform: 'none' },
                    },
                  }}
                >
                  <Typography variant="h2" component="span" sx={{ display: 'block', mb: 1 }}>
                    {category.emoji}
                  </Typography>
                  <Typography variant="h6" fontWeight={600}>{t(`home.${category.key}`)}</Typography>
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
  const addItem = useCartStore((state) => state.addItem)
  useExposeAuthToken()
  useCartBridge()
  useInventoryStream()

  // The catalog MFE's `onAddToCart` only carries productId/quantity, so the shell fetches the
  // product details it needs to build a cart line item. Store-only (no cart-service POST), same
  // as cart-mfe's own addItem — cart-service sync isn't wired up anywhere yet (see useCartSync
  // stub in cart-mfe).
  const handleAddToCart = async (productId: string, quantity: number) => {
    try {
      const product = await productService.getProductById(productId)
      for (let i = 0; i < quantity; i += 1) {
        addItem({ productId: product.id, name: product.name, price: product.price, image: product.images[0] })
      }
    } catch {
      // Product lookup failed — nothing is added to the cart.
    }
  }

  return (
    <MainLayout cartItemCount={cartItemCount}>
      <RouteProgressBar />
      {isLoading ? (
        <Box sx={{ width: '100%', px: { xs: 2, md: 4 } }}>
          <PageSkeleton />
        </Box>
      ) : (
        <PageTransition>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route
              path="/products/*"
              element={<MFERoute mfeName="productCatalog" componentProps={{ onAddToCart: handleAddToCart }} />}
            />
            <Route
              path="/categories/*"
              element={<MFERoute mfeName="productCatalog" componentProps={{ onAddToCart: handleAddToCart }} />}
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
              element={
                <MFERouteGuard mfeName="adminDashboard">
                  <Box sx={{ width: '100%', px: { xs: 2, md: 4 } }}>
                    <MFEErrorBoundary mfeName="adminDashboard">
                      <MicroFrontendLoader mfeName="adminDashboard" />
                    </MFEErrorBoundary>
                  </Box>
                </MFERouteGuard>
              }
            />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </PageTransition>
      )}
    </MainLayout>
  )
}

export default App
