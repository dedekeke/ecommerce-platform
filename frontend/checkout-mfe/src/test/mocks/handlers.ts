import { http, HttpResponse } from 'msw'
import type { CheckoutRequestPayload, GuestCheckoutRequestPayload } from '../../api/types'

const API_BASE = 'http://localhost:8080/api'

// Order-first checkout (PR#122): POST /orders creates the order AND the Stripe PaymentIntent
// server-side, returning a clientSecret for Stripe Elements. The cart is not sent by the client.
export const handlers = [
  http.post(`${API_BASE}/orders`, async ({ request }) => {
    const body = (await request.json()) as CheckoutRequestPayload
    if (!body.shippingAddress?.street) {
      return HttpResponse.json({ message: 'Shipping address is required' }, { status: 400 })
    }
    return HttpResponse.json(
      {
        orderId: 'order-123',
        orderNumber: 'ORD-20260429-001',
        status: 'PENDING',
        currency: 'USD',
        subtotal: 79.99,
        tax: 8.0,
        shippingCost: 5.99,
        discountAmount: null,
        loyaltyDiscount: null,
        total: 93.98,
        paymentIntentId: 'pi_test_123',
        clientSecret: 'pi_test_123_secret_abc',
        items: [
          { productId: 'prod-1', productName: 'Headphones', price: 79.99, quantity: 1, subtotal: 79.99 },
        ],
        createdAt: new Date().toISOString(),
      },
      { status: 201 }
    )
  }),

  // Guest checkout: no userId; identity derived server-side from the email.
  // Same 201 + clientSecret contract as the authenticated create.
  http.post(`${API_BASE}/orders/guest`, async ({ request }) => {
    const body = (await request.json()) as GuestCheckoutRequestPayload
    if (!body.email) {
      return HttpResponse.json({ message: 'Email is required for guest checkout' }, { status: 400 })
    }
    if (!body.shippingAddress?.street) {
      return HttpResponse.json({ message: 'Shipping address is required' }, { status: 400 })
    }
    return HttpResponse.json(
      {
        orderId: 'guest-order-123',
        orderNumber: 'ORD-20260429-777',
        status: 'PENDING',
        currency: 'USD',
        subtotal: 79.99,
        tax: 8.0,
        shippingCost: 5.99,
        discountAmount: null,
        loyaltyDiscount: null,
        total: 93.98,
        paymentIntentId: 'pi_guest_123',
        clientSecret: 'pi_guest_123_secret_abc',
        items: [
          { productId: 'prod-1', productName: 'Headphones', price: 79.99, quantity: 1, subtotal: 79.99 },
        ],
        createdAt: new Date().toISOString(),
      },
      { status: 201 }
    )
  }),

  http.get(`${API_BASE}/orders/:orderId`, ({ params }) => {
    const { orderId } = params
    return HttpResponse.json({
      id: orderId,
      orderNumber: 'ORD-20260429-001',
      status: 'PENDING',
      items: [{ productId: 'prod-1', quantity: 2, price: 79.99 }],
      shippingAddress: {
        fullName: 'Jane Doe',
        line1: '123 Main St',
        city: 'San Francisco',
        state: 'CA',
        postalCode: '94105',
        country: 'US',
      },
      totalAmount: 168.97,
      createdAt: '2026-04-29T00:00:00.000Z',
    })
  }),
]
