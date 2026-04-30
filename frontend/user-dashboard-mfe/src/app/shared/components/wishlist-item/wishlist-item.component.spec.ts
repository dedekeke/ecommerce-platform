import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WishlistItemComponent } from './wishlist-item.component';
import { WishlistItem } from '../../../core/models/wishlist.model';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

const mockItem: WishlistItem = {
  id: 'wish-1',
  productId: 'prod-1',
  productName: 'Cool Sneakers',
  imageUrl: 'https://example.com/sneakers.jpg',
  price: 129.99,
  currency: 'USD',
  addedAt: '2024-01-10T10:00:00Z',
  inStock: true,
};

describe('WishlistItemComponent', () => {
  let fixture: ComponentFixture<WishlistItemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WishlistItemComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(WishlistItemComponent);
    fixture.componentRef.setInput('item', mockItem);
    fixture.detectChanges();
  });

  it('should display product name', () => {
    expect(fixture.nativeElement.textContent).toContain('Cool Sneakers');
  });

  it('should display the price', () => {
    expect(fixture.nativeElement.textContent).toContain('129.99');
  });

  it('should show in-stock indicator when inStock is true', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('In Stock');
  });

  it('should emit remove event when remove button is clicked', () => {
    let emitted: string | undefined;
    fixture.componentInstance.remove.subscribe((id: string) => (emitted = id));

    const removeBtn = fixture.nativeElement.querySelector('[data-testid="remove-btn"]');
    removeBtn?.click();
    expect(emitted).toBe('wish-1');
  });

  it('should emit addToCart event when add-to-cart button is clicked', () => {
    let emitted: WishlistItem | undefined;
    fixture.componentInstance.addToCart.subscribe((item: WishlistItem) => (emitted = item));

    const addBtn = fixture.nativeElement.querySelector('[data-testid="add-to-cart-btn"]');
    addBtn?.click();
    expect(emitted).toEqual(mockItem);
  });

  it('should show out-of-stock state when inStock is false', async () => {
    fixture.componentRef.setInput('item', { ...mockItem, inStock: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Out of Stock');
  });
});
