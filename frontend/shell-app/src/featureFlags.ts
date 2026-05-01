/**
 * Frontend feature-flag SDK (§4.6).
 *
 * Reads VITE_FEATURE_FLAG_<NAME> from `import.meta.env`. Naming mirrors the
 * backend convention so a flag is identified by the same UPPER_SNAKE_CASE
 * name on both ends:
 *
 *   - Backend env var:  FEATURE_FLAG_RECOMMENDATIONS=true
 *   - Frontend env var: VITE_FEATURE_FLAG_RECOMMENDATIONS=true
 *
 * Build-time substitution by Vite means the flag value is baked into the
 * bundle — flipping a flag therefore requires a rebuild, just like the backend
 * needs a redeploy. That's the trade-off we accept for the in-house provider;
 * see docs/FEATURE_FLAGS.md for the migration path to a real (runtime)
 * provider when the flag count grows past ~10.
 */

import { useMemo } from 'react'

const FLAG_PREFIX = 'VITE_FEATURE_FLAG_' as const

/**
 * Read a feature flag synchronously. Returns `false` for unknown flags or
 * any value that doesn't case-insensitively equal `"true"`.
 *
 * @param flagName UPPER_SNAKE_CASE flag name, no `VITE_FEATURE_FLAG_` prefix
 */
export function isFeatureEnabled(flagName: string): boolean {
  if (!flagName || !flagName.trim()) {
    return false
  }
  const key = `${FLAG_PREFIX}${flagName}`
  // Vite replaces `import.meta.env` references at build time, so this is
  // the production source of truth. We also check `process.env` to support
  // unit tests that use `vi.stubEnv` (Vitest 4 stubs `process.env` but not
  // the frozen `import.meta.env` keys that didn't exist at build time).
  const metaEnv = (import.meta as ImportMeta).env as Record<string, string | undefined>
  const procEnv = (typeof process !== 'undefined' && process.env)
    ? (process.env as Record<string, string | undefined>)
    : undefined
  const raw = metaEnv?.[key] ?? procEnv?.[key]
  return typeof raw === 'string' && raw.toLowerCase() === 'true'
}

/**
 * React hook wrapper around {@link isFeatureEnabled}. Memoised on the flag
 * name so consumers can drop it into render trees without worrying about
 * stable references.
 */
export function useFeatureFlag(flagName: string): boolean {
  return useMemo(() => isFeatureEnabled(flagName), [flagName])
}
