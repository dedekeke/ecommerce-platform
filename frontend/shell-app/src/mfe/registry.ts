import type { MFEConfig, MFEName } from './types'

const MFE_BASE_URLS = {
  productCatalog: import.meta.env.VITE_MFE_PRODUCT_CATALOG_URL || 'http://localhost:5001',
  cart: import.meta.env.VITE_MFE_CART_URL || 'http://localhost:5002',
  checkout: import.meta.env.VITE_MFE_CHECKOUT_URL || 'http://localhost:5003',
  userDashboard: import.meta.env.VITE_MFE_USER_DASHBOARD_URL || 'http://localhost:5004',
  adminDashboard: import.meta.env.VITE_MFE_ADMIN_DASHBOARD_URL || 'http://localhost:5005',
} as const

export const mfeRegistry: Record<MFEName, MFEConfig> = {
  productCatalog: {
    name: 'productCatalog',
    displayName: 'Product Catalog',
    remoteUrl: `${MFE_BASE_URLS.productCatalog}/assets/remoteEntry.js`,
    exposedModule: './ProductCatalog',
    fallbackSkeleton: 'productList',
    requiresAuth: false,
    runtime: 'webpack',
  },
  cart: {
    name: 'cart',
    displayName: 'Shopping Cart',
    remoteUrl: `${MFE_BASE_URLS.cart}/assets/remoteEntry.js`,
    exposedModule: './Cart',
    fallbackSkeleton: 'cart',
    requiresAuth: false,
    runtime: 'webpack',
  },
  checkout: {
    name: 'checkout',
    displayName: 'Checkout',
    remoteUrl: `${MFE_BASE_URLS.checkout}/assets/remoteEntry.js`,
    exposedModule: './Checkout',
    fallbackSkeleton: 'page',
    requiresAuth: true,
    runtime: 'webpack',
  },
  userDashboard: {
    name: 'userDashboard',
    displayName: 'User Dashboard',
    remoteUrl: `${MFE_BASE_URLS.userDashboard}/remoteEntry.json`,
    exposedModule: './UserDashboard',
    fallbackSkeleton: 'profile',
    requiresAuth: true,
    runtime: 'native',
    basePath: '/profile',
  },
  adminDashboard: {
    name: 'adminDashboard',
    displayName: 'Admin Dashboard',
    remoteUrl: `${MFE_BASE_URLS.adminDashboard}/remoteEntry.json`,
    exposedModule: './AdminDashboard',
    fallbackSkeleton: 'page',
    requiresAuth: true,
    requiredRoles: ['admin'],
    runtime: 'native',
    basePath: '/admin',
  },
}

export function getMFEConfig(name: MFEName): MFEConfig {
  const config = mfeRegistry[name]
  if (!config) {
    throw new Error(`MFE "${name}" not found in registry`)
  }
  return config
}

export function getAllMFENames(): MFEName[] {
  return Object.keys(mfeRegistry) as MFEName[]
}

export function getPublicMFEs(): MFEConfig[] {
  return Object.values(mfeRegistry).filter((config) => !config.requiresAuth)
}

export function getProtectedMFEs(): MFEConfig[] {
  return Object.values(mfeRegistry).filter((config) => config.requiresAuth)
}
