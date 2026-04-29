import { http, HttpResponse } from 'msw'
import { mockCart, mockEmptyCart } from './cart'

const API_BASE = 'http://localhost:8080/api'

export const handlers = [
  http.get(`${API_BASE}/cart`, () => {
    return HttpResponse.json(mockCart)
  }),

  http.post(`${API_BASE}/cart/items`, async ({ request }) => {
    const body = (await request.json()) as { productId: string; quantity: number }
    const newItem = {
      itemId: `item-new-${Date.now()}`,
      productId: body.productId,
      name: 'New Product',
      price: 29.99,
      quantity: body.quantity,
    }
    return HttpResponse.json(
      {
        ...mockCart,
        items: [...mockCart.items, newItem],
        itemCount: mockCart.itemCount + body.quantity,
        total: mockCart.total + 29.99 * body.quantity,
      },
      { status: 201 }
    )
  }),

  http.put(`${API_BASE}/cart/items/:itemId`, async ({ params, request }) => {
    const { itemId } = params
    const body = (await request.json()) as { quantity: number }
    const updatedItems = mockCart.items.map((item) =>
      item.itemId === itemId ? { ...item, quantity: body.quantity } : item
    )
    return HttpResponse.json({ ...mockCart, items: updatedItems })
  }),

  http.delete(`${API_BASE}/cart/items/:itemId`, ({ params }) => {
    const { itemId } = params
    const remaining = mockCart.items.filter((item) => item.itemId !== itemId)
    return HttpResponse.json({ ...mockCart, items: remaining })
  }),

  http.delete(`${API_BASE}/cart/clear`, () => {
    return HttpResponse.json(mockEmptyCart)
  }),
]

export { mockCart, mockEmptyCart }
