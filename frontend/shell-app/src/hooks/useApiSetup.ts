import { useEffect, useRef } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { useNavigate } from 'react-router-dom'
import {
  setupAuthInterceptor,
  setupResponseInterceptor,
  removeAuthInterceptor,
  removeResponseInterceptor,
} from '../api'
import { useNotifications } from './useNotifications'

export function useApiSetup() {
  const { getAccessTokenSilently, isAuthenticated, logout } = useAuth0()
  const navigate = useNavigate()
  const { showError } = useNotifications()

  const authInterceptorRef = useRef<number | null>(null)
  const responseInterceptorRef = useRef<number | null>(null)

  useEffect(() => {
    const getToken = async (): Promise<string | null> => {
      if (!isAuthenticated) {
        return null
      }

      try {
        const token = await getAccessTokenSilently()
        return token
      } catch (error) {
        console.error('Failed to get access token:', error)
        return null
      }
    }

    authInterceptorRef.current = setupAuthInterceptor(getToken)

    responseInterceptorRef.current = setupResponseInterceptor(
      () => {
        showError('Your session has expired. Please log in again.')
        logout({ logoutParams: { returnTo: window.location.origin } })
      },
      () => {
        showError('You do not have permission to perform this action.')
        navigate('/')
      }
    )

    return () => {
      if (authInterceptorRef.current !== null) {
        removeAuthInterceptor(authInterceptorRef.current)
      }
      if (responseInterceptorRef.current !== null) {
        removeResponseInterceptor(responseInterceptorRef.current)
      }
    }
  }, [isAuthenticated, getAccessTokenSilently, logout, navigate, showError])
}

export default useApiSetup
