import { initFederation } from '@angular-architects/native-federation-runtime'

function buildManifest(): Record<string, string> {
  const userDashboardUrl =
    (import.meta.env.VITE_MFE_USER_DASHBOARD_URL as string | undefined) || 'http://localhost:5004'
  const adminDashboardUrl =
    (import.meta.env.VITE_MFE_ADMIN_DASHBOARD_URL as string | undefined) || 'http://localhost:5005'

  return {
    userDashboard: `${userDashboardUrl}/remoteEntry.json`,
    adminDashboard: `${adminDashboardUrl}/remoteEntry.json`,
  }
}

let initialized = false
let initPromise: Promise<void> | null = null

export async function initNativeFederation(): Promise<void> {
  if (initialized) {
    return
  }

  if (initPromise) {
    return initPromise
  }

  initPromise = (async () => {
    try {
      await initFederation(buildManifest())
      initialized = true
    } catch (err) {
      initPromise = null
      throw err
    }
  })()

  return initPromise
}

export function isNativeFederationInitialized(): boolean {
  return initialized
}

export function resetNativeFederationState(): void {
  initialized = false
  initPromise = null
}
