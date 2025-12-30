import { Routes, Route } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { Container, Typography, Box, Avatar, Paper, Grid } from '@mui/material'
import { MainLayout } from './components/layout'
import { ProtectedRoute } from './components/auth'
import { PageSkeleton } from './components/common'
import { useCartStore, selectCartItemCount } from './stores'

function Home() {
  return (
    <Container maxWidth="lg">
      <Box sx={{ textAlign: 'center', py: 8 }}>
        <Typography variant="h2" component="h1" gutterBottom fontWeight={700}>
          Welcome to E-Commerce
        </Typography>
        <Typography variant="h5" color="text.secondary" sx={{ mb: 4 }}>
          Discover amazing products at great prices
        </Typography>
        <Grid container spacing={3} sx={{ mt: 4 }}>
          {['Electronics', 'Fashion', 'Home & Garden'].map((category) => (
            <Grid size={{ xs: 12, md: 4 }} key={category}>
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
                <Typography variant="h6">{category}</Typography>
              </Paper>
            </Grid>
          ))}
        </Grid>
      </Box>
    </Container>
  )
}

function Profile() {
  const { user } = useAuth0()

  return (
    <Container maxWidth="md">
      <Typography variant="h4" component="h1" gutterBottom fontWeight={600}>
        My Profile
      </Typography>
      {user && (
        <Paper sx={{ p: 4 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, mb: 4 }}>
            <Avatar
              src={user.picture}
              alt={user.name}
              sx={{ width: 100, height: 100 }}
            />
            <Box>
              <Typography variant="h5" fontWeight={600}>
                {user.name}
              </Typography>
              <Typography variant="body1" color="text.secondary">
                {user.email}
              </Typography>
            </Box>
          </Box>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Typography variant="subtitle2" color="text.secondary">
                Email Verified
              </Typography>
              <Typography variant="body1">
                {user.email_verified ? 'Yes' : 'No'}
              </Typography>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Typography variant="subtitle2" color="text.secondary">
                Last Updated
              </Typography>
              <Typography variant="body1">
                {user.updated_at
                  ? new Date(user.updated_at).toLocaleDateString()
                  : 'N/A'}
              </Typography>
            </Grid>
          </Grid>
        </Paper>
      )}
    </Container>
  )
}

function Products() {
  return (
    <Container maxWidth="lg">
      <Typography variant="h4" component="h1" gutterBottom fontWeight={600}>
        Products
      </Typography>
      <Typography color="text.secondary">
        Product listing will be loaded from the Product Catalog micro-frontend.
      </Typography>
    </Container>
  )
}

function Cart() {
  return (
    <Container maxWidth="lg">
      <Typography variant="h4" component="h1" gutterBottom fontWeight={600}>
        Shopping Cart
      </Typography>
      <Typography color="text.secondary">
        Cart contents will be loaded from the Cart micro-frontend.
      </Typography>
    </Container>
  )
}

function App() {
  const { isLoading } = useAuth0()
  const cartItemCount = useCartStore(selectCartItemCount)

  return (
    <MainLayout cartItemCount={cartItemCount}>
      {isLoading ? (
        <Container maxWidth="lg">
          <PageSkeleton />
        </Container>
      ) : (
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/products" element={<Products />} />
          <Route path="/categories" element={<Products />} />
          <Route path="/cart" element={<Cart />} />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <Profile />
              </ProtectedRoute>
            }
          />
          <Route
            path="/orders"
            element={
              <ProtectedRoute>
                <Container maxWidth="lg">
                  <Typography variant="h4" gutterBottom fontWeight={600}>
                    My Orders
                  </Typography>
                  <Typography color="text.secondary">
                    Order history will be displayed here.
                  </Typography>
                </Container>
              </ProtectedRoute>
            }
          />
        </Routes>
      )}
    </MainLayout>
  )
}

export default App
