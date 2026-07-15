import { describe, it, expect } from 'vitest'
import { toAddressDto } from './toAddressDto'
import type { ShippingAddress } from '../api/types'

const baseAddress: ShippingAddress = {
  fullName: 'Jane Doe',
  line1: '123 Main St',
  city: 'San Francisco',
  state: 'CA',
  postalCode: '94105',
  country: 'US',
}

describe('toAddressDto', () => {
  it('should map line1 to street when line2 is absent', () => {
    expect(toAddressDto(baseAddress)).toEqual({
      street: '123 Main St',
      city: 'San Francisco',
      state: 'CA',
      postalCode: '94105',
      country: 'US',
    })
  })

  it('should fold line2 into street when present', () => {
    const withLine2 = { ...baseAddress, line2: 'Apt 4' }
    expect(toAddressDto(withLine2).street).toBe('123 Main St, Apt 4')
  })

  it('should not include fullName in the output', () => {
    const dto = toAddressDto(baseAddress)
    expect(dto).not.toHaveProperty('fullName')
  })
})
