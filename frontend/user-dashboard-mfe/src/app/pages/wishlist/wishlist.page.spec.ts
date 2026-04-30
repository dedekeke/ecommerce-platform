import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WishlistPage } from './wishlist.page';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { WishlistService } from '../../core/services/wishlist.service';
import { BehaviorSubject } from 'rxjs';
import { WishlistItem } from '../../core/models/wishlist.model';

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

describe('WishlistPage', () => {
  let fixture: ComponentFixture<WishlistPage>;
  let wishlistServiceSpy: jasmine.SpyObj<WishlistService>;
  let itemsSubject: BehaviorSubject<WishlistItem[]>;

  beforeEach(async () => {
    itemsSubject = new BehaviorSubject<WishlistItem[]>(mockItems);
    wishlistServiceSpy = jasmine.createSpyObj('WishlistService', ['getItems', 'removeItem']);
    wishlistServiceSpy.getItems.and.returnValue(itemsSubject.asObservable());

    await TestBed.configureTestingModule({
      imports: [WishlistPage, NoopAnimationsModule],
      providers: [{ provide: WishlistService, useValue: wishlistServiceSpy }],
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
});
