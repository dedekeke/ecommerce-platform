/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_AUTH0_DOMAIN: string
  readonly VITE_AUTH0_CLIENT_ID: string
  readonly VITE_AUTH0_AUDIENCE: string
  readonly VITE_AUTH0_REDIRECT_URI: string
  /** 'mock' enables the dev-server-only local test auth mode — see src/auth/mockAuth.ts */
  readonly VITE_AUTH_MODE?: string
  readonly VITE_MFE_PRODUCT_CATALOG_URL?: string
  readonly VITE_MFE_CART_URL?: string
  readonly VITE_MFE_CHECKOUT_URL?: string
  readonly VITE_MFE_USER_DASHBOARD_URL?: string
  readonly VITE_MFE_ADMIN_DASHBOARD_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module 'productCatalog/ProductCatalog' {
  const Component: React.ComponentType<unknown>
  export default Component
}

declare module 'cart/Cart' {
  const Component: React.ComponentType<unknown>
  export default Component
}

declare module 'checkout/Checkout' {
  const Component: React.ComponentType<unknown>
  export default Component
}

declare module 'userDashboard/UserDashboard' {
  const Component: React.ComponentType<unknown>
  export default Component
}

declare module 'adminDashboard/AdminDashboard' {
  const Component: React.ComponentType<unknown>
  export default Component
}

/** Payload accepted by `window.__cartBridge.addItem` — mirrors the shell cart store's `CartItem`. */
interface CartBridgeItem {
  productId: string
  name: string
  price: number
  image?: string
  /** Number of units to add; defaults to 1. */
  quantity?: number
}

interface CartBridge {
  addItem: (item: CartBridgeItem) => void
}

interface Window {
  __getAuthToken?: () => Promise<string | null>
  __getAuthUserId?: () => string | null
  __ecommerceToastHost?: boolean
  __cartBridge?: CartBridge
}

/** Minimal process declaration for Vitest's `vi.stubEnv` compatibility (no @types/node needed). */
declare const process: {
  env: Record<string, string | undefined>
}
