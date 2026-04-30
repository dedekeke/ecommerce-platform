import { TestBed } from '@angular/core/testing';
import { WishlistService } from './wishlist.service';

const STORAGE_KEY = 'user-wishlist-storage';

describe('WishlistService', () => {
  let service: WishlistService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [WishlistService] });
    service = TestBed.inject(WishlistService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should start with an empty wishlist', (done) => {
    service.getItems().subscribe((items) => {
      expect(items.length).toBe(0);
      done();
    });
  });

  it('should add an item and persist it to localStorage', (done) => {
    service.addItem({
      productId: 'prod-1',
      productName: 'Test Product',
      price: 9.99,
      currency: 'USD',
      inStock: true,
    });

    service.getItems().subscribe((items) => {
      expect(items.length).toBe(1);
      expect(items[0].productName).toBe('Test Product');
      const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
      expect(stored.length).toBe(1);
      done();
    });
  });

  it('should not add duplicate items for the same productId', () => {
    service.addItem({ productId: 'prod-1', productName: 'A', price: 1, currency: 'USD', inStock: true });
    service.addItem({ productId: 'prod-1', productName: 'A', price: 1, currency: 'USD', inStock: true });

    let count = 0;
    service.getItems().subscribe((items) => (count = items.length));
    expect(count).toBe(1);
  });

  it('should remove an item by id', (done) => {
    service.addItem({ productId: 'prod-1', productName: 'A', price: 1, currency: 'USD', inStock: true });

    service.getItems().subscribe((items) => {
      if (items.length === 1) {
        service.removeItem(items[0].id);
      }
    });

    service.getItems().subscribe((items) => {
      if (items.length === 0) {
        done();
      }
    });

    service.removeItem('non-existent-id');
    const items = (service as unknown as { items$: { getValue: () => unknown[] } }).items$.getValue();
    expect(items.length).toBe(0);
    done();
  });

  it('should return true from isInWishlist for added product', () => {
    service.addItem({ productId: 'prod-2', productName: 'B', price: 2, currency: 'USD', inStock: false });
    expect(service.isInWishlist('prod-2')).toBeTrue();
  });

  it('should return false from isInWishlist for missing product', () => {
    expect(service.isInWishlist('prod-99')).toBeFalse();
  });

  it('should load items from localStorage on init', () => {
    const stored = [
      { id: 'w1', productId: 'p1', productName: 'Stored', price: 5, currency: 'USD', addedAt: '2024-01-01T00:00:00Z', inStock: true },
    ];
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [WishlistService] });
    const freshService = TestBed.inject(WishlistService);

    let loadedItems: unknown[] = [];
    freshService.getItems().subscribe((items) => (loadedItems = items));
    expect(loadedItems.length).toBe(1);
  });
});
