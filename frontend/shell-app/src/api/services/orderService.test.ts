import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { orderService } from './orderService'
import { apiClient } from '../apiClient'
import type {
  Order,
  CreateOrderRequest,
  OrderSearchParams,
  ValidatePromotionRequest,
  ValidatePromotionResponse,
} from '../types'

vi.mock('../apiClient', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockOrder: Order = {
  id: 'order-1',
  orderNumber: 'ORD-2025-00001',
  userId: 'user-1',
  items: [
    {
      productId: 'prod-1',
      productName: 'Test Product',
      productSku: 'SKU001',
      price: 99.99,
      quantity: 2,
      subtotal: 199.98,
    },
  ],
  subtotal: 199.98,
  tax: 16.00,
  shippingCost: 5.99,
  total: 221.97,
  status: 'PENDING',
  shippingAddress: {
    id: 'addr-1',
    label: 'Home',
    street: '123 Main St',
    city: 'Springfield',
    state: 'IL',
    postalCode: '62701',
    country: 'US',
    isDefault: true,
  },
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
}

const mockOrders: Order[] = [
  mockOrder,
  {
    ...mockOrder,
    id: 'order-2',
    orderNumber: 'ORD-2025-00002',
    status: 'DELIVERED',
  },
]

const mockPaginatedOrders = {
  content: mockOrders,
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
  first: true,
  last: true,
}

describe('OrderService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getOrders', () => {
    it('should fetch user orders with default parameters', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockPaginatedOrders })

      const result = await orderService.getOrders()

      expect(apiClient.get).toHaveBeenCalledWith('/orders', { params: {} })
      expect(result).toEqual(mockPaginatedOrders)
    })

    it('should fetch orders with search parameters', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockPaginatedOrders })

      const params: OrderSearchParams = {
        status: 'DELIVERED',
        page: 0,
        size: 10,
      }

      const result = await orderService.getOrders(params)

      expect(apiClient.get).toHaveBeenCalledWith('/orders', { params })
      expect(result).toEqual(mockPaginatedOrders)
    })
  })

  describe('getOrderById', () => {
    it('should fetch a single order by ID', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockOrder })

      const result = await orderService.getOrderById('order-1')

      expect(apiClient.get).toHaveBeenCalledWith('/orders/order-1')
      expect(result).toEqual(mockOrder)
    })
  })

  describe('getOrderByNumber', () => {
    it('should fetch an order by order number', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: mockOrder })

      const result = await orderService.getOrderByNumber('ORD-2025-00001')

      expect(apiClient.get).toHaveBeenCalledWith('/orders/number/ORD-2025-00001')
      expect(result).toEqual(mockOrder)
    })
  })

  describe('createOrder', () => {
    it('should create a new order', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockOrder })

      const request: CreateOrderRequest = {
        shippingAddressId: 'addr-1',
      }

      const result = await orderService.createOrder(request)

      expect(apiClient.post).toHaveBeenCalledWith('/orders', request)
      expect(result).toEqual(mockOrder)
    })

    it('should create order with promotion code', async () => {
      const orderWithDiscount = {
        ...mockOrder,
        promotionCode: 'SAVE10',
        discountAmount: 20.00,
        total: 201.97,
      }
      vi.mocked(apiClient.post).mockResolvedValue({ data: orderWithDiscount })

      const request: CreateOrderRequest = {
        shippingAddressId: 'addr-1',
        promotionCode: 'SAVE10',
      }

      const result = await orderService.createOrder(request)

      expect(apiClient.post).toHaveBeenCalledWith('/orders', request)
      expect(result.promotionCode).toBe('SAVE10')
    })
  })

  describe('cancelOrder', () => {
    it('should cancel an order', async () => {
      const cancelledOrder = { ...mockOrder, status: 'CANCELLED' as const }
      vi.mocked(apiClient.put).mockResolvedValue({ data: cancelledOrder })

      const result = await orderService.cancelOrder('order-1')

      expect(apiClient.put).toHaveBeenCalledWith('/orders/order-1/cancel')
      expect(result.status).toBe('CANCELLED')
    })
  })

  describe('trackOrder', () => {
    it('should fetch order tracking information', async () => {
      const trackingInfo = {
        orderId: 'order-1',
        status: 'SHIPPED',
        trackingNumber: 'TRACK123',
        carrier: 'UPS',
        estimatedDelivery: '2025-01-05T00:00:00Z',
        events: [
          {
            timestamp: '2025-01-03T10:00:00Z',
            status: 'SHIPPED',
            location: 'Distribution Center',
            description: 'Package shipped',
          },
        ],
      }
      vi.mocked(apiClient.get).mockResolvedValue({ data: trackingInfo })

      const result = await orderService.trackOrder('order-1')

      expect(apiClient.get).toHaveBeenCalledWith('/orders/order-1/tracking')
      expect(result).toEqual(trackingInfo)
    })
  })

  describe('validatePromotion', () => {
    it('should validate a promotion code', async () => {
      const validResponse: ValidatePromotionResponse = {
        valid: true,
        promotion: {
          id: 'promo-1',
          code: 'SAVE10',
          name: 'Save 10%',
          type: 'PERCENTAGE',
          discountValue: 10,
          currentUses: 50,
          startDate: '2025-01-01T00:00:00Z',
          endDate: '2025-12-31T23:59:59Z',
          active: true,
        },
        discountAmount: 20.00,
      }
      vi.mocked(apiClient.post).mockResolvedValue({ data: validResponse })

      const request: ValidatePromotionRequest = {
        code: 'SAVE10',
        subtotal: 200.00,
      }

      const result = await orderService.validatePromotion(request)

      expect(apiClient.post).toHaveBeenCalledWith('/promotions/validate', request)
      expect(result.valid).toBe(true)
      expect(result.discountAmount).toBe(20.00)
    })

    it('should return invalid for expired promotion', async () => {
      const invalidResponse: ValidatePromotionResponse = {
        valid: false,
        message: 'Promotion code has expired',
      }
      vi.mocked(apiClient.post).mockResolvedValue({ data: invalidResponse })

      const request: ValidatePromotionRequest = {
        code: 'EXPIRED',
        subtotal: 200.00,
      }

      const result = await orderService.validatePromotion(request)

      expect(result.valid).toBe(false)
      expect(result.message).toBe('Promotion code has expired')
    })
  })

  describe('reorder', () => {
    it('should create a new order from previous order', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockOrder })

      const result = await orderService.reorder('order-1')

      expect(apiClient.post).toHaveBeenCalledWith('/orders/order-1/reorder')
      expect(result).toEqual(mockOrder)
    })
  })
})
