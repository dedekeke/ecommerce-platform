import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WishlistPage } from './wishlist.page';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { WishlistService } from '../../core/services/wishlist.service';
import { CartService } from '../../core/services/cart.service';
import { ToastService } from '../../core/services/toast.service';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { WishlistItem } from '../../core/models/wishlist.model';
import { CartResponse } from '../../core/models/cart.model';

const mockItems: WishlistItem[] = [
  {
    id: 'wish-1',
    productId: 'prod-1',
    productName: 'Blue Sneakers',
    price: 99.99,
    currency: 'USD',
    addedAt: '2024-01-01T00:00:00Z',
    inStock: true,
  },
];

const mockCart: CartResponse = {
  cartId: 'cart-1',
  items: [{ itemId: 'item-1', productId: 'prod-1', name: 'Blue Sneakers', price: 99.99, quantity: 1 }],
  total: 99.99,
  itemCount: 1,
};

describe('WishlistPage', () => {
  let fixture: ComponentFixture<WishlistPage>;
  let wishlistServiceSpy: jasmine.SpyObj<WishlistService>;
  let cartServiceSpy: jasmine.SpyObj<CartService>;
  let toastServiceSpy: jasmine.SpyObj<ToastService>;
  let itemsSubject: BehaviorSubject<WishlistItem[]>;

  beforeEach(async () => {
    itemsSubject = new BehaviorSubject<WishlistItem[]>(mockItems);
    wishlistServiceSpy = jasmine.createSpyObj('WishlistService', ['getItems', 'removeItem']);
    wishlistServiceSpy.getItems.and.returnValue(itemsSubject.asObservable());
    cartServiceSpy = jasmine.createSpyObj('CartService', ['addItem']);
    cartServiceSpy.addItem.and.returnValue(of(mockCart));
    toastServiceSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [WishlistPage, NoopAnimationsModule],
      providers: [
        { provide: WishlistService, useValue: wishlistServiceSpy },
        { provide: CartService, useValue: cartServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WishlistPage);
    fixture.detectChanges();
  });

  it('should display page title', () => {
    expect(fixture.nativeElement.textContent).toContain('Wishlist');
  });

  it('should render wishlist items', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const items = fixture.nativeElement.querySelectorAll('app-wishlist-item');
    expect(items.length).toBe(1);
  });

  it('should call removeItem when item fires remove event', () => {
    fixture.componentInstance.onRemoveItem('wish-1');
    expect(wishlistServiceSpy.removeItem).toHaveBeenCalledWith('wish-1');
  });

  it('should show empty state when wishlist is empty', async () => {
    itemsSubject.next([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No favorites yet');
  });

  it('should call cartService.addItem with the productId and a quantity of 1', () => {
    fixture.componentInstance.onAddToCart(mockItems[0]);
    expect(cartServiceSpy.addItem).toHaveBeenCalledWith({ productId: 'prod-1', quantity: 1 });
  });

  it('should show a success toast when the item is added to the cart', () => {
    fixture.componentInstance.onAddToCart(mockItems[0]);
    expect(toastServiceSpy.success).toHaveBeenCalledWith('Added to cart');
  });

  it('should not throw and should not toast success when the add-to-cart request fails', () => {
    cartServiceSpy.addItem.and.returnValue(throwError(() => new Error('conflict')));
    expect(() => fixture.componentInstance.onAddToCart(mockItems[0])).not.toThrow();
    expect(toastServiceSpy.success).not.toHaveBeenCalled();
  });
});
