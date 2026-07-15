import type { AddressDto, ShippingAddress } from '../api/types'

/**
 * Maps the client's ShippingAddress (collected by AddressForm) to the AddressDto shape expected
 * by `POST /api/orders` (order-service AddressDto: street/city/state/postalCode/country — no
 * fullName or line2). `line2`, if present, is folded into `street` so apartment/suite info is
 * not lost; `fullName` is not part of this DTO — pass it separately as
 * `CheckoutRequestPayload.userName` if you have a use for it (e.g. the confirmation email).
 */
export function toAddressDto(address: ShippingAddress): AddressDto {
  return {
    street: address.line2 ? `${address.line1}, ${address.line2}` : address.line1,
    city: address.city,
    state: address.state,
    postalCode: address.postalCode,
    country: address.country,
  }
}
