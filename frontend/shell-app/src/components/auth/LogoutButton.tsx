import { useAuth0 } from '@auth0/auth0-react'
import { type ReactNode, type ButtonHTMLAttributes } from 'react'

interface LogoutButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onClick'> {
  children?: ReactNode
}

export const LogoutButton = ({
  children = 'Log Out',
  className = '',
  ...props
}: LogoutButtonProps) => {
  const { isAuthenticated, isLoading, logout } = useAuth0()

  if (!isAuthenticated) {
    return null
  }

  return (
    <button
      onClick={() =>
        logout({
          logoutParams: {
            returnTo: window.location.origin,
          },
        })
      }
      disabled={isLoading}
      className={`logout-button ${className}`.trim()}
      {...props}
    >
      {children}
    </button>
  )
}
