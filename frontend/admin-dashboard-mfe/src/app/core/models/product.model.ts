// Aligned to product-service dto/ProductResponse + dto/ProductRequest
// (same class of contract fix as commit 130ad52 did for Order).

export interface ProductCategory {
  id: number;
  name: string;
  slug?: string;
}

export interface ProductDimensions {
  length?: number;
  width?: number;
  height?: number;
  weight?: number;
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  description?: string | null;
  category?: ProductCategory | null;
  price: number;
  currency: string;
  images?: string[] | null;
  dimensions?: ProductDimensions | null;
  stockQuantity: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  // Computed by product-service
  inStock?: boolean;
  available?: boolean;
}

export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK';

export function productStatus(p: Pick<Product, 'active' | 'inStock' | 'stockQuantity'>): ProductStatus {
  if (!p.active) return 'INACTIVE';
  if (p.inStock === false || p.stockQuantity === 0) return 'OUT_OF_STOCK';
  return 'ACTIVE';
}

/**
 * Mirrors ProductRequest: sku, name, price and currency are mandatory.
 * PUT /api/products/{id} takes the FULL request (no partial update), so
 * create and update share this shape.
 */
export interface ProductPayload {
  sku: string;
  name: string;
  description?: string;
  categoryId?: number;
  price: number;
  currency: string;
  images: string[];
  dimensions?: ProductDimensions;
  stockQuantity: number;
  active?: boolean;
}

/** Mirrors product-service PageResponse<T>. */
export interface PagedProducts {
  content: Product[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Mirrors GET /api/products query params (sortBy/sortDirection/categoryId). */
export interface ProductFilterParams {
  page: number;
  size: number;
  sortBy?: string;
  sortDirection?: 'asc' | 'desc';
  search?: string;
  categoryId?: string;
}
