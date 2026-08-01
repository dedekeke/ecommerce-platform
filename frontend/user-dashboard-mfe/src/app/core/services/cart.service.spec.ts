import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CartService } from './cart.service';
import { CartResponse } from '../models/cart.model';

describe('CartService', () => {
  let service: CartService;
  let httpMock: HttpTestingController;

  const mockCart: CartResponse = {
    cartId: 'cart-1',
    items: [{ itemId: 'item-1', productId: 'prod-1', name: 'Blue Sneakers', price: 99.99, quantity: 1 }],
    total: 99.99,
    itemCount: 1,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CartService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CartService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should POST /api/cart/items with productId and quantity when adding an item', () => {
    let received: CartResponse | undefined;
    service.addItem({ productId: 'prod-1', quantity: 1 }).subscribe((cart) => (received = cart));

    const req = httpMock.expectOne('/api/cart/items');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ productId: 'prod-1', quantity: 1 });
    req.flush(mockCart);

    expect(received).toEqual(mockCart);
  });

  it('should propagate errors from the cart endpoint', () => {
    let receivedError: unknown;
    service.addItem({ productId: 'prod-1', quantity: 1 }).subscribe({
      error: (err) => (receivedError = err),
    });

    const req = httpMock.expectOne('/api/cart/items');
    req.flush({ message: 'Product out of stock' }, { status: 409, statusText: 'Conflict' });

    expect(receivedError).toBeTruthy();
  });
});
