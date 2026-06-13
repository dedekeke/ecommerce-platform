import { type ReactNode } from 'react'
import { ProtectedRoute } from './ProtectedRoute'
import { RoleGuard } from './RoleGuard'
import { getMFEConfig } from '../../mfe/registry'
import type { MFEName } from '../../mfe/types'

interface MFERouteGuardProps {
  /** Registry key for the MFE — requiredRoles is read from its MFEConfig */
  mfeName: MFEName
  children: ReactNode
}

/**
 * Composes ProtectedRoute (auth check) and RoleGuard (role check) driven
 * entirely by the mfeRegistry entry for the given MFE.
 *
 * This is the single source of truth for access control on Angular (native)
 * and React MFE routes: the registry's requiredRoles array drives the guard
 * without the caller having to repeat role strings in JSX.
 */
export function MFERouteGuard({ mfeName, children }: MFERouteGuardProps) {
  const config = getMFEConfig(mfeName)

  if (!config.requiresAuth) {
    return <>{children}</>
  }

  const roles = config.requiredRoles ?? []

  return (
    <ProtectedRoute>
      <RoleGuard requiredRoles={roles}>{children}</RoleGuard>
    </ProtectedRoute>
  )
}
