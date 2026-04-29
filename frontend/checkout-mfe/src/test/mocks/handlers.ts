import { http, HttpResponse } from 'msw'
import type { CreateOrderPayload } from '../../api/types'

const API_BASE = 'http://localhost:8080/api'

export const handlers = [
  http.post(`${API_BASE}/orders`, async ({ request }) => {
    const body = (await request.json()) as CreateOrderPayload
    if (!body.userId || !body.items?.length) {
      return HttpResponse.json({ message: 'Invalid order payload' }, { status: 400 })
    }
    return HttpResponse.json(
      {
        id: 'order-123',
        orderNumber: 'ORD-20260429-001',
        status: 'PENDING',
        items: body.items,
        shippingAddress: body.shippingAddress,
        totalAmount: body.totalAmount,
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
