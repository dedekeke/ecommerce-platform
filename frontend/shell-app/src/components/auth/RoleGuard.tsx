import { type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { Box, Typography, Button, Paper, Container } from '@mui/material'
import { LockOutlined as LockIcon } from '@mui/icons-material'

const ROLES_CLAIM = 'https://ecommerce-platform.com/roles'

interface RoleGuardProps {
  requiredRoles: string[]
  children: ReactNode
}

function ForbiddenPage() {
  return (
    <Container maxWidth="sm">
      <Paper
        data-testid="forbidden-page"
        elevation={0}
        sx={{
          p: 6,
          mt: 8,
          textAlign: 'center',
          borderRadius: 2,
          bgcolor: 'grey.50',
          border: '1px solid',
          borderColor: 'divider',
        }}
      >
        <LockIcon sx={{ fontSize: 64, mb: 2, color: 'error.main', opacity: 0.7 }} />

        <Typography variant="h3" component="h1" gutterBottom fontWeight={700} color="error.main">
          403
        </Typography>

        <Typography variant="h6" gutterBottom>
          Access Forbidden
        </Typography>

        <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
          You do not have permission to view this page. If you believe this is an error, please
          contact your administrator.
        </Typography>

        <Box sx={{ display: 'flex', justifyContent: 'center' }}>
          <Button
            component={Link}
            to="/"
            variant="contained"
            color="primary"
            aria-label="Go home"
          >
            Go Home
          </Button>
        </Box>
      </Paper>
    </Container>
  )
}

export function RoleGuard({ requiredRoles, children }: RoleGuardProps) {
  const { isAuthenticated, user } = useAuth0()

  if (requiredRoles.length === 0) {
    return <>{children}</>
  }

  if (!isAuthenticated || !user) {
    return <ForbiddenPage />
  }

  const userRoles: string[] = (user[ROLES_CLAIM] as string[] | undefined) ?? []
  const hasRequiredRole = requiredRoles.some((role) => userRoles.includes(role))

  if (!hasRequiredRole) {
    return <ForbiddenPage />
  }

  return <>{children}</>
}
