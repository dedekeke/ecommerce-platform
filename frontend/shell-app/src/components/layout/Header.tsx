import { useState, useEffect, useCallback } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { useTranslation } from 'react-i18next'
import {
  Toolbar,
  Typography,
  Button,
  IconButton,
  InputBase,
  Badge,
  Box,
  Menu,
  MenuItem,
  Avatar,
  Divider,
  useTheme,
  useMediaQuery,
} from '@mui/material'
import {
  Menu as MenuIcon,
  Search as SearchIcon,
  ShoppingCart as ShoppingCartIcon,
  Person as PersonIcon,
  Logout as LogoutIcon,
  LightMode as LightModeIcon,
  DarkMode as DarkModeIcon,
} from '@mui/icons-material'
import { alpha, styled } from '@mui/material/styles'
import { useColorMode } from '../../hooks/useColorMode'
import { designTokens } from '../../theme'
import { CurrencyPicker } from '../common/CurrencyPicker'
import { LanguagePicker } from '../common/LanguagePicker'

interface HeaderProps {
  cartItemCount: number
  onMenuClick: () => void
}

const Search = styled('div')(({ theme }) => ({
  position: 'relative',
  borderRadius: theme.shape.borderRadius,
  backgroundColor: alpha(theme.palette.text.primary, 0.06),
  '&:hover': {
    backgroundColor: alpha(theme.palette.text.primary, 0.1),
  },
  marginRight: theme.spacing(2),
  marginLeft: 0,
  width: '100%',
  [theme.breakpoints.up('sm')]: {
    marginLeft: theme.spacing(3),
    width: 'auto',
  },
  transition: `background-color ${designTokens.duration.fast} ${designTokens.easing.out}`,
}))

const SearchIconWrapper = styled('div')(({ theme }) => ({
  padding: theme.spacing(0, 2),
  height: '100%',
  position: 'absolute',
  pointerEvents: 'none',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
}))

const StyledInputBase = styled(InputBase)(({ theme }) => ({
  color: 'inherit',
  width: '100%',
  '& .MuiInputBase-input': {
    padding: theme.spacing(1, 1, 1, 0),
    paddingLeft: `calc(1em + ${theme.spacing(4)})`,
    transition: theme.transitions.create('width'),
    [theme.breakpoints.up('md')]: {
      width: '20ch',
      '&:focus': {
        width: '30ch',
      },
    },
  },
}))

const NavLink = styled(RouterLink)(({ theme }) => ({
  color: theme.palette.text.primary,
  textDecoration: 'none',
  marginRight: theme.spacing(3),
  fontWeight: 500,
  fontSize: '0.9rem',
  padding: '6px 12px',
  borderRadius: 8,
  transition: `all ${designTokens.duration.fast} ${designTokens.easing.out}`,
  '&:hover': {
    color: theme.palette.primary.main,
    backgroundColor: alpha(theme.palette.primary.main, 0.08),
  },
}))

export const Header = ({ cartItemCount, onMenuClick }: HeaderProps) => {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const { isAuthenticated, isLoading, user, loginWithRedirect, logout } = useAuth0()
  const { resolvedMode, toggle } = useColorMode()
  const { t } = useTranslation()

  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)
  const [scrolled, setScrolled] = useState(false)
  const isMenuOpen = Boolean(anchorEl)

  const handleScroll = useCallback(() => {
    setScrolled(window.scrollY > 80)
  }, [])

  useEffect(() => {
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [handleScroll])

  const handleUserMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget)
  }

  const handleUserMenuClose = () => {
    setAnchorEl(null)
  }

  const handleLogout = () => {
    handleUserMenuClose()
    logout({ logoutParams: { returnTo: window.location.origin } })
  }

  const glassBg =
    resolvedMode === 'dark'
      ? designTokens.glass.bgDark
      : designTokens.glass.bg

  const headerSx = {
    position: 'sticky' as const,
    top: 0,
    zIndex: theme.zIndex.appBar,
    width: '100%',
    backdropFilter: designTokens.glass.blur,
    WebkitBackdropFilter: designTokens.glass.blur,
    backgroundColor: glassBg,
    borderBottom: `1px solid ${scrolled
      ? resolvedMode === 'dark' ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)'
      : 'transparent'}`,
    boxShadow: scrolled ? designTokens.shadows.elevation : 'none',
    transition: `box-shadow ${designTokens.duration.normal} ${designTokens.easing.out}, border-color ${designTokens.duration.normal} ${designTokens.easing.out}`,
    '@media (prefers-reduced-motion: reduce)': {
      transition: 'none',
    },
  }

  return (
    <Box component="header" sx={headerSx} data-testid="app-header">
      <Toolbar>
        <IconButton
          edge="start"
          aria-label="menu"
          onClick={onMenuClick}
          data-testid="mobile-menu-button"
          sx={{ mr: 2, display: { md: 'none' }, color: 'text.primary' }}
        >
          <MenuIcon />
        </IconButton>

        <Typography
          variant="h6"
          component={RouterLink}
          to="/"
          sx={{
            textDecoration: 'none',
            color: 'text.primary',
            fontWeight: 700,
            flexGrow: { xs: 1, md: 0 },
            letterSpacing: '-0.02em',
          }}
        >
          E-Commerce
        </Typography>

        {!isMobile && (
          <Box sx={{ display: 'flex', alignItems: 'center', ml: 4 }}>
            <NavLink to="/">{t('nav.home')}</NavLink>
            <NavLink to="/products">{t('nav.products')}</NavLink>
            <NavLink to="/categories">{t('nav.categories')}</NavLink>
          </Box>
        )}

        <Box sx={{ flexGrow: 1, display: { xs: 'none', md: 'flex' }, justifyContent: 'center' }}>
          <Search>
            <SearchIconWrapper>
              <SearchIcon sx={{ color: 'text.secondary' }} />
            </SearchIconWrapper>
            <StyledInputBase
              placeholder={t('header.search')}
              inputProps={{ 'aria-label': 'search' }}
            />
          </Search>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <IconButton
            onClick={toggle}
            aria-label={resolvedMode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            data-testid="theme-toggle-button"
            sx={{
              color: 'text.primary',
              transition: `transform ${designTokens.duration.fast} ${designTokens.easing.out}`,
              '&:hover': { transform: 'rotate(15deg)' },
              '@media (prefers-reduced-motion: reduce)': {
                '&:hover': { transform: 'none' },
              },
            }}
          >
            {resolvedMode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
          </IconButton>

          <IconButton
            component={RouterLink}
            to="/cart"
            aria-label="shopping cart"
            sx={{ color: 'text.primary' }}
          >
            <Badge
              badgeContent={cartItemCount}
              color="primary"
              data-testid="cart-badge"
            >
              <ShoppingCartIcon />
            </Badge>
          </IconButton>

          {!isMobile && <LanguagePicker />}
          {!isMobile && <CurrencyPicker />}

          {isAuthenticated && user ? (
            <>
              <IconButton
                onClick={handleUserMenuOpen}
                data-testid="user-menu-button"
                aria-label={t('header.userMenu')}
                aria-haspopup="menu"
                aria-expanded={isMenuOpen || undefined}
                sx={{ ml: 0.5 }}
              >
                <Avatar
                  src={user.picture}
                  alt={user.name}
                  sx={{ width: 32, height: 32 }}
                />
              </IconButton>
              <Menu
                anchorEl={anchorEl}
                open={isMenuOpen}
                onClose={handleUserMenuClose}
                transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                slotProps={{
                  paper: {
                    sx: {
                      mt: 0.5,
                      borderRadius: 2,
                      minWidth: 180,
                      boxShadow: designTokens.shadows.lg,
                    },
                  },
                }}
              >
                <MenuItem disabled>
                  <Typography variant="body2" color="text.secondary">
                    {user.email}
                  </Typography>
                </MenuItem>
                <Divider />
                <MenuItem
                  component={RouterLink}
                  to="/profile"
                  onClick={handleUserMenuClose}
                >
                  <PersonIcon fontSize="small" sx={{ mr: 1 }} />
                  {t('nav.profile')}
                </MenuItem>
                <MenuItem
                  component={RouterLink}
                  to="/orders"
                  onClick={handleUserMenuClose}
                >
                  <ShoppingCartIcon fontSize="small" sx={{ mr: 1 }} />
                  {t('nav.myOrders')}
                </MenuItem>
                <Divider />
                <MenuItem onClick={handleLogout}>
                  <LogoutIcon fontSize="small" sx={{ mr: 1 }} />
                  {t('nav.logOut')}
                </MenuItem>
              </Menu>
            </>
          ) : (
            <Button
              variant="contained"
              onClick={() => loginWithRedirect()}
              disabled={isLoading}
              sx={{
                ml: 1,
                borderRadius: designTokens.radius.full,
                px: 3,
              }}
            >
              {t('nav.logIn')}
            </Button>
          )}
        </Box>
      </Toolbar>
    </Box>
  )
}
