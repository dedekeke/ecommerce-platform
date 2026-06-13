/**
 * Feature-flag SDK for shell-app.
 *
 * `isFeatureEnabled` is the shared, framework-agnostic implementation from
 * @ecommerce/shared-ui. `useFeatureFlag` is the React hook wrapper kept here
 * so the shared package stays dependency-free.
 */
import { useMemo } from 'react'
import { isFeatureEnabled as _isFeatureEnabled } from '@ecommerce/shared-ui/featureFlags'

export { _isFeatureEnabled as isFeatureEnabled }

/** React hook wrapper around {@link isFeatureEnabled}. */
export function useFeatureFlag(flagName: string): boolean {
  return useMemo(() => _isFeatureEnabled(flagName), [flagName])
}
