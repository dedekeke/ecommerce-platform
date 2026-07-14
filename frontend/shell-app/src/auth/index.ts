// NOTE: MockAuthProvider is intentionally NOT re-exported here — it must only
// be reached via the DEV-gated dynamic import in Auth0ProviderWithNavigate so
// production build graphs exclude the mock chunk entirely.
export { isMockAuthMode } from './mockAuth'
