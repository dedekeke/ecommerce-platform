import { useAuth0 } from '@auth0/auth0-react'
import { type ReactNode, type ButtonHTMLAttributes } from 'react'

interface LoginButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onClick'> {
  children?: ReactNode
}

export const LoginButton = ({
  children = 'Log In',
  className = '',
  ...props
}: LoginButtonProps) => {
  const { isAuthenticated, isLoading, loginWithRedirect } = useAuth0()

  if (isAuthenticated) {
    return null
  }

  return (
    <button
      onClick={() => loginWithRedirect()}
      disabled={isLoading}
      className={`login-button ${className}`.trim()}
      {...props}
    >
      {children}
    </button>
  )
}
