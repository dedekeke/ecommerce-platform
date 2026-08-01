import { http, HttpResponse } from 'msw'
import { mockCart, mockEmptyCart } from './cart'
import { mockValidDiscount, mockInvalidDiscount } from './promotion'

const API_BASE = 'http://localhost:8080/api'

export const handlers = [
  http.get(`${API_BASE}/cart`, () => {
    return HttpResponse.json(mockCart)
  }),

  http.post(`${API_BASE}/cart/items`, async ({ request }) => {
    const body = (await request.json()) as { productId: string; quantity: number }
    const newItem = {
      id: `item-new-${Date.now()}`,
      productId: body.productId,
      productName: 'New Product',
      productSku: 'SKU-NEW',
      productImageUrl: null,
      price: 29.99,
      quantity: body.quantity,
      subtotal: 29.99 * body.quantity,
    }
    return HttpResponse.json({
      ...mockCart,
      items: [...mockCart.items, newItem],
      totalItems: mockCart.totalItems + body.quantity,
      totalAmount: mockCart.totalAmount + 29.99 * body.quantity,
    })
  }),

  http.put(`${API_BASE}/cart/items/:itemId`, async ({ params, request }) => {
    const { itemId } = params
    const body = (await request.json()) as { quantity: number }
    const updatedItems = mockCart.items.map((item) =>
      item.id === itemId ? { ...item, quantity: body.quantity } : item
    )
    return HttpResponse.json({ ...mockCart, items: updatedItems })
  }),

  http.delete(`${API_BASE}/cart/items/:itemId`, ({ params }) => {
    const { itemId } = params
    const remaining = mockCart.items.filter((item) => item.id !== itemId)
    return HttpResponse.json({ ...mockCart, items: remaining })
  }),

  // Matches cart-service: DELETE /api/cart/clear responds 204 No Content.
  http.delete(`${API_BASE}/cart/clear`, () => {
    return new HttpResponse(null, { status: 204 })
  }),

  http.post(`${API_BASE}/promotions/validate`, async ({ request }) => {
    const body = (await request.json()) as { code: string }
    return HttpResponse.json(
      body.code.toUpperCase() === 'SAVE10' ? mockValidDiscount : mockInvalidDiscount
    )
  }),
]

export { mockCart, mockEmptyCart, mockValidDiscount, mockInvalidDiscount }
