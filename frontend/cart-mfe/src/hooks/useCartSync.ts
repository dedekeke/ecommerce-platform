// Stub for Day 40 shell integration. Will sync local store with backend on auth.
export function useCartSync(_isAuthenticated: boolean) {
  return { synced: false, syncing: false }
}
