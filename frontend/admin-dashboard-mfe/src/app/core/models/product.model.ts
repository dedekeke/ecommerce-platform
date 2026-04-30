export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  categoryId: string;
  imageUrls: string[];
  stock: number;
  sku: string;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
}

export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK';

export interface CreateProductPayload {
  name: string;
  description: string;
  price: number;
  categoryId: string;
  imageUrls: string[];
  stock: number;
  sku: string;
}

export interface UpdateProductPayload {
  name?: string;
  description?: string;
  price?: number;
  categoryId?: string;
  imageUrls?: string[];
  stock?: number;
}

export interface PagedProducts {
  content: Product[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface ProductFilterParams {
  page: number;
  size: number;
  sort?: string;
  direction?: 'asc' | 'desc';
  search?: string;
  category?: string;
}
