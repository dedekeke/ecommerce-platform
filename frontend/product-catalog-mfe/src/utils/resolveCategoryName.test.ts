import { describe, it, expect } from 'vitest'
import { resolveCategoryName } from './resolveCategoryName'
import { mockCategories } from '../test/mocks/products'

describe('resolveCategoryName', () => {
  it('should return undefined for a null categoryId', () => {
    expect(resolveCategoryName(mockCategories, null)).toBeUndefined()
  })

  it('should resolve a root category by id', () => {
    expect(resolveCategoryName(mockCategories, '1')).toBe('Electronics')
  })

  it('should resolve a root category by slug', () => {
    expect(resolveCategoryName(mockCategories, 'clothing')).toBe('Clothing')
  })

  it('should resolve a nested child category by slug', () => {
    expect(resolveCategoryName(mockCategories, 'smartphones')).toBe('Smartphones')
  })

  it('should return undefined for an unknown categoryId', () => {
    expect(resolveCategoryName(mockCategories, 'does-not-exist')).toBeUndefined()
  })
})
