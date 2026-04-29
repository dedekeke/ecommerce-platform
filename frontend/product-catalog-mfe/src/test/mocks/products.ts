import type { Product, Category, PaginatedResponse } from '../../types'

export const mockCategory: Category = {
  id: '1',
  name: 'Electronics',
  slug: 'electronics',
  description: 'Electronic devices and accessories',
  active: true,
  displayOrder: 1,
  level: 0,
  fullPath: 'Electronics',
  hasChildren: true,
  children: [
    {
      id: '2',
      name: 'Smartphones',
      slug: 'smartphones',
      parentId: '1',
      parentName: 'Electronics',
      active: true,
      displayOrder: 1,
      level: 1,
      fullPath: 'Electronics / Smartphones',
      hasChildren: false,
    },
  ],
}

export const mockCategories: Category[] = [
  mockCategory,
  {
    id: '3',
    name: 'Clothing',
    slug: 'clothing',
    description: 'Fashion and apparel',
    active: true,
    displayOrder: 2,
    level: 0,
    fullPath: 'Clothing',
    hasChildren: false,
  },
]

export const mockProduct: Product = {
  id: '1',
  sku: 'PROD-001',
  name: 'Test Product',
  description: 'This is a test product description that provides details about the product.',
  category: mockCategory,
  price: 99.99,
  currency: 'USD',
  images: [
    'https://picsum.photos/seed/prod1/400/400',
    'https://picsum.photos/seed/prod1b/400/400',
  ],
  dimensions: {
    length: 10,
    width: 5,
    height: 2,
    weight: 0.5,
  },
  stockQuantity: 50,
  active: true,
  inStock: true,
  available: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
}

export const mockProducts: Product[] = [
  mockProduct,
  {
    id: '2',
    sku: 'PROD-002',
    name: 'Another Product',
    description: 'Another product description.',
    category: mockCategory,
    price: 149.99,
    currency: 'USD',
    images: ['https://picsum.photos/seed/prod2/400/400'],
    stockQuantity: 25,
    active: true,
    inStock: true,
    available: true,
    createdAt: '2024-01-02T00:00:00Z',
    updatedAt: '2024-01-02T00:00:00Z',
  },
  {
    id: '3',
    sku: 'PROD-003',
    name: 'Out of Stock Product',
    description: 'This product is currently out of stock.',
    category: mockCategory,
    price: 79.99,
    currency: 'USD',
    images: ['https://picsum.photos/seed/prod3/400/400'],
    stockQuantity: 0,
    active: true,
    inStock: false,
    available: false,
    createdAt: '2024-01-03T00:00:00Z',
    updatedAt: '2024-01-03T00:00:00Z',
  },
  {
    id: '4',
    sku: 'PROD-004',
    name: 'Premium Product',
    description: 'A premium high-end product.',
    category: mockCategories[1],
    price: 299.99,
    currency: 'USD',
    images: ['https://picsum.photos/seed/prod4/400/400'],
    stockQuantity: 10,
    active: true,
    inStock: true,
    available: true,
    createdAt: '2024-01-04T00:00:00Z',
    updatedAt: '2024-01-04T00:00:00Z',
  },
]

export const mockPaginatedProducts: PaginatedResponse<Product> = {
  content: mockProducts,
  page: 0,
  size: 12,
  totalElements: mockProducts.length,
  totalPages: 1,
  first: true,
  last: true,
}

export function createMockProduct(overrides: Partial<Product> = {}): Product {
  return {
    ...mockProduct,
    id: Math.random().toString(36).substring(7),
    ...overrides,
  }
}

export function createMockPaginatedResponse(
  products: Product[],
  page = 0,
  size = 12
): PaginatedResponse<Product> {
  return {
    content: products,
    page,
    size,
    totalElements: products.length,
    totalPages: Math.ceil(products.length / size),
    first: page === 0,
    last: page >= Math.ceil(products.length / size) - 1,
  }
}
