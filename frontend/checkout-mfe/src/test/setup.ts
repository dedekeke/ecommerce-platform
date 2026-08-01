import '@testing-library/jest-dom'
import { beforeEach } from 'vitest'

export const TEST_AUTH_USER_ID = 'auth0|test-user'

// Checkout is gated behind auth by the shell; tests run authenticated by default.
// Auth-gate tests delete the accessor to simulate the unauthenticated case.
beforeEach(() => {
  window.__getAuthUserId = () => TEST_AUTH_USER_ID
})
