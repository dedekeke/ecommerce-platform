import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from './api-client.service';
import {
  Product,
  PagedProducts,
  ProductFilterParams,
  CreateProductPayload,
  UpdateProductPayload,
} from '../models/product.model';

@Injectable({ providedIn: 'root' })
export class ProductAdminService {
  private readonly api = inject(ApiClientService);

  getProducts(params: ProductFilterParams): Observable<PagedProducts> {
    const queryParams: Record<string, string | number | boolean> = {
      page: params.page,
      size: params.size,
    };
    if (params.sort) queryParams['sort'] = params.sort;
    if (params.direction) queryParams['direction'] = params.direction;
    if (params.search) queryParams['search'] = params.search;
    if (params.category) queryParams['category'] = params.category;
    return this.api.get<PagedProducts>('/products', queryParams);
  }

  getProductById(id: string): Observable<Product> {
    return this.api.get<Product>(`/products/${id}`);
  }

  createProduct(payload: CreateProductPayload): Observable<Product> {
    return this.api.post<Product>('/products', payload);
  }

  updateProduct(id: string, payload: UpdateProductPayload): Observable<Product> {
    return this.api.put<Product>(`/products/${id}`, payload);
  }

  deleteProduct(id: string): Observable<void> {
    return this.api.delete<void>(`/products/${id}`);
  }
}
