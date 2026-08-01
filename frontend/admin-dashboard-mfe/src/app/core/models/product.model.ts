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
 * POST /api/products takes the full request; `stockQuantity` seeds the catalog
 * stock snapshot at creation time.
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

/**
 * PUT /api/products/{id} takes the FULL request too, but `stockQuantity` is
 * CREATE-ONLY and is ignored server-side (inventory-service owns stock movement;
 * echoing back a snapshot read at form-open time would clobber concurrent stock
 * changes). Stock edits go through PATCH /api/products/{id}/stock instead —
 * see ProductAdminService.updateStock.
 */
export type ProductUpdatePayload = Omit<ProductPayload, 'stockQuantity'>;

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
