import { describe, it, expect } from 'vitest'
import {
  mfeRegistry,
  getMFEConfig,
  getAllMFENames,
  getPublicMFEs,
  getProtectedMFEs,
} from './registry'
import type { MFEName } from './types'

describe('mfeRegistry', () => {
  it('should contain all expected MFE configurations', () => {
    const expectedMFEs: MFEName[] = [
      'productCatalog',
      'cart',
      'checkout',
      'userDashboard',
      'adminDashboard',
    ]

    expectedMFEs.forEach((name) => {
      expect(mfeRegistry[name]).toBeDefined()
    })
  })

  it('should have valid configuration for each MFE', () => {
    Object.entries(mfeRegistry).forEach(([name, config]) => {
      expect(config.name).toBe(name)
      expect(config.displayName).toBeTruthy()
      expect(config.remoteUrl).toMatch(/remoteEntry\.(js|json)$/)
      expect(config.exposedModule).toMatch(/^\.\//)
      expect(['page', 'productList', 'cart', 'profile']).toContain(config.fallbackSkeleton)
      expect(typeof config.requiresAuth).toBe('boolean')
      expect(['webpack', 'native']).toContain(config.runtime)
    })
  })

  describe('productCatalog config', () => {
    it('should be public (no auth required)', () => {
      expect(mfeRegistry.productCatalog.requiresAuth).toBe(false)
    })

    it('should use productList skeleton', () => {
      expect(mfeRegistry.productCatalog.fallbackSkeleton).toBe('productList')
    })

    it('should use webpack runtime', () => {
      expect(mfeRegistry.productCatalog.runtime).toBe('webpack')
    })
  })

  describe('cart config', () => {
    it('should be public (no auth required)', () => {
      expect(mfeRegistry.cart.requiresAuth).toBe(false)
    })

    it('should use cart skeleton', () => {
      expect(mfeRegistry.cart.fallbackSkeleton).toBe('cart')
    })

    it('should use webpack runtime', () => {
      expect(mfeRegistry.cart.runtime).toBe('webpack')
    })
  })

  describe('checkout config', () => {
    it('should require authentication', () => {
      expect(mfeRegistry.checkout.requiresAuth).toBe(true)
    })
  })

  describe('userDashboard config', () => {
    it('should require authentication', () => {
      expect(mfeRegistry.userDashboard.requiresAuth).toBe(true)
    })

    it('should use profile skeleton', () => {
      expect(mfeRegistry.userDashboard.fallbackSkeleton).toBe('profile')
    })

    it('should use native runtime', () => {
      expect(mfeRegistry.userDashboard.runtime).toBe('native')
    })

    it('should point remoteUrl to remoteEntry.json', () => {
      expect(mfeRegistry.userDashboard.remoteUrl).toContain('remoteEntry.json')
    })
  })

  describe('adminDashboard config', () => {
    it('should require authentication', () => {
      expect(mfeRegistry.adminDashboard.requiresAuth).toBe(true)
    })

    it('should require admin role', () => {
      expect(mfeRegistry.adminDashboard.requiredRoles).toContain('admin')
    })

    it('should use native runtime', () => {
      expect(mfeRegistry.adminDashboard.runtime).toBe('native')
    })

    it('should point remoteUrl to remoteEntry.json', () => {
      expect(mfeRegistry.adminDashboard.remoteUrl).toContain('remoteEntry.json')
    })
  })
})

describe('getMFEConfig', () => {
  it('should return config for valid MFE name', () => {
    const config = getMFEConfig('productCatalog')
    expect(config.name).toBe('productCatalog')
    expect(config.displayName).toBe('Product Catalog')
  })

  it('should throw error for invalid MFE name', () => {
    expect(() => getMFEConfig('invalid' as MFEName)).toThrow(
      'MFE "invalid" not found in registry'
    )
  })
})

describe('getAllMFENames', () => {
  it('should return all MFE names', () => {
    const names = getAllMFENames()
    expect(names).toHaveLength(5)
    expect(names).toContain('productCatalog')
    expect(names).toContain('cart')
    expect(names).toContain('checkout')
    expect(names).toContain('userDashboard')
    expect(names).toContain('adminDashboard')
  })
})

describe('getPublicMFEs', () => {
  it('should return only MFEs that do not require auth', () => {
    const publicMFEs = getPublicMFEs()
    expect(publicMFEs.every((mfe) => !mfe.requiresAuth)).toBe(true)
  })

  it('should include productCatalog and cart', () => {
    const publicMFEs = getPublicMFEs()
    const names = publicMFEs.map((mfe) => mfe.name)
    expect(names).toContain('productCatalog')
    expect(names).toContain('cart')
  })

  it('should not include protected MFEs', () => {
    const publicMFEs = getPublicMFEs()
    const names = publicMFEs.map((mfe) => mfe.name)
    expect(names).not.toContain('checkout')
    expect(names).not.toContain('userDashboard')
    expect(names).not.toContain('adminDashboard')
  })
})

describe('getProtectedMFEs', () => {
  it('should return only MFEs that require auth', () => {
    const protectedMFEs = getProtectedMFEs()
    expect(protectedMFEs.every((mfe) => mfe.requiresAuth)).toBe(true)
  })

  it('should include checkout, userDashboard, and adminDashboard', () => {
    const protectedMFEs = getProtectedMFEs()
    const names = protectedMFEs.map((mfe) => mfe.name)
    expect(names).toContain('checkout')
    expect(names).toContain('userDashboard')
    expect(names).toContain('adminDashboard')
  })

  it('should not include public MFEs', () => {
    const protectedMFEs = getProtectedMFEs()
    const names = protectedMFEs.map((mfe) => mfe.name)
    expect(names).not.toContain('productCatalog')
    expect(names).not.toContain('cart')
  })
})
