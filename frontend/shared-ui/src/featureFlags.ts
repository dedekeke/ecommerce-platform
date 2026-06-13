/**
 * Shared feature-flag SDK — framework-agnostic core (§4.6).
 *
 * Single source of truth consumed by shell-app and every MFE via the
 * @ecommerce/shared-ui path alias. Intentionally has zero runtime dependencies
 * so it can be compiled under any app's TypeScript project without needing a
 * separate node_modules install in this package.
 *
 * React hook wrapper (`useFeatureFlag`) lives in each app's local featureFlags.ts
 * which re-exports this function alongside the hook.
 *
 * Dual-read strategy:
 *   1. import.meta.env[key] — works in built Vite browser bundles where Vite
 *      replaces static references but exposes dynamic bracket access at runtime.
 *   2. process.env[key]     — patched by vi.stubEnv in Vitest; env.d.ts carries
 *      a minimal `process` declaration so @types/node is not needed.
 * The nullish-coalesce falls through to process.env so tests always win.
 */

const FLAG_PREFIX = 'VITE_FEATURE_FLAG_' as const

/**
 * Read a feature flag synchronously. Returns `false` for unknown flags or
 * any value that is not case-insensitively equal to `"true"`.
 *
 * @param flagName UPPER_SNAKE_CASE flag name without the `VITE_FEATURE_FLAG_` prefix
 */
export function isFeatureEnabled(flagName: string): boolean {
  if (!flagName || !flagName.trim()) {
    return false
  }
  const key = `${FLAG_PREFIX}${flagName}`
  // import.meta.env is the canonical Vite browser runtime source; process.env
  // is the vi.stubEnv-patched fallback for Vitest (see env.d.ts).
  const raw = import.meta.env[key] ?? process.env[key]
  return typeof raw === 'string' && raw.toLowerCase() === 'true'
}
