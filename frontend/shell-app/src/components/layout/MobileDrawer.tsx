import { Link as RouterLink } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import {
  Drawer,
  Box,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  IconButton,
  Typography,
  Divider,
  Avatar,
  Button,
} from '@mui/material'
import {
  Close as CloseIcon,
  Home as HomeIcon,
  Category as CategoryIcon,
  ShoppingBag as ShoppingBagIcon,
  ShoppingCart as ShoppingCartIcon,
  Person as PersonIcon,
  Receipt as ReceiptIcon,
  Logout as LogoutIcon,
} from '@mui/icons-material'

interface MobileDrawerProps {
  open: boolean
  onClose: () => void
}

const navItems = [
  { label: 'Home', path: '/', icon: <HomeIcon /> },
  { label: 'Products', path: '/products', icon: <ShoppingBagIcon /> },
  { label: 'Categories', path: '/categories', icon: <CategoryIcon /> },
  { label: 'Cart', path: '/cart', icon: <ShoppingCartIcon /> },
]

const userNavItems = [
  { label: 'Profile', path: '/profile', icon: <PersonIcon /> },
  { label: 'My Orders', path: '/orders', icon: <ReceiptIcon /> },
]

export const MobileDrawer = ({ open, onClose }: MobileDrawerProps) => {
  const { isAuthenticated, user, loginWithRedirect, logout } = useAuth0()

  const handleLogout = () => {
    onClose()
    logout({ logoutParams: { returnTo: window.location.origin } })
  }

  return (
    <Drawer
      anchor="left"
      open={open}
      onClose={onClose}
      // todo: deprecated
      PaperProps={{
        sx: { width: 280 },
      }}
    >
      <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h6" fontWeight={600}>
          Menu
        </Typography>
        <IconButton onClick={onClose} data-testid="drawer-close-button">
          <CloseIcon />
        </IconButton>
      </Box>

      <Divider />

      {isAuthenticated && user && (
        <>
          <Box sx={{ p: 2, display: 'flex', alignItems: 'center' }}>
            <Avatar src={user.picture} alt={user.name} sx={{ mr: 2 }} />
            <Box>
              <Typography variant="subtitle1" fontWeight={500}>
                {user.name || 'Test User'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {user.email}
              </Typography>
            </Box>
          </Box>
          <Divider />
        </>
      )}

      <List>
        {navItems.map((item) => (
          <ListItem key={item.path} disablePadding>
            <ListItemButton
              component={RouterLink}
              to={item.path}
              onClick={onClose}
            >
              <ListItemIcon>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          </ListItem>
        ))}
      </List>

      {isAuthenticated && (
        <>
          <Divider />
          <List>
            {userNavItems.map((item) => (
              <ListItem key={item.path} disablePadding>
                <ListItemButton
                  component={RouterLink}
                  to={item.path}
                  onClick={onClose}
                >
                  <ListItemIcon>{item.icon}</ListItemIcon>
                  <ListItemText primary={item.label} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </>
      )}

      <Box sx={{ flexGrow: 1 }} />

      <Divider />

      <Box sx={{ p: 2 }}>
        {isAuthenticated ? (
          <Button
            fullWidth
            variant="outlined"
            startIcon={<LogoutIcon />}
            onClick={handleLogout}
          >
            Log Out
          </Button>
        ) : (
          <Button
            fullWidth
            variant="contained"
            onClick={() => {
              onClose()
              loginWithRedirect()
            }}
          >
            Log In
          </Button>
        )}
      </Box>
    </Drawer>
  )
}
