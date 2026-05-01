/**
 * Frontend feature-flag SDK (§4.6).
 *
 * Mirror of `frontend/shell-app/src/featureFlags.ts`. Each MFE keeps a local
 * copy so the module is bundled into the remote chunk and stays self-sufficient
 * (no Module Federation share needed for one-file utilities). When we adopt a
 * real provider per `docs/FEATURE_FLAGS.md`, both copies will be replaced
 * with imports of the new provider's React SDK.
 */

import { useMemo } from 'react'

const FLAG_PREFIX = 'VITE_FEATURE_FLAG_' as const

export function isFeatureEnabled(flagName: string): boolean {
  if (!flagName || !flagName.trim()) {
    return false
  }
  const key = `${FLAG_PREFIX}${flagName}`
  const metaEnv = (import.meta as ImportMeta).env as Record<string, string | undefined>
  const procEnv = (typeof process !== 'undefined' && process.env)
    ? (process.env as Record<string, string | undefined>)
    : undefined
  const raw = metaEnv?.[key] ?? procEnv?.[key]
  return typeof raw === 'string' && raw.toLowerCase() === 'true'
}

export function useFeatureFlag(flagName: string): boolean {
  return useMemo(() => isFeatureEnabled(flagName), [flagName])
}
