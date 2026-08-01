import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AddressCardComponent } from './address-card.component';
import { Address } from '../../../core/models/user.model';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

const mockAddress: Address = {
  id: 'addr-1',
  userId: 'user-1',
  label: 'Home',
  firstName: 'John',
  lastName: 'Doe',
  street: '123 Main St',
  city: 'New York',
  state: 'NY',
  postalCode: '10001',
  country: 'US',
  isDefault: true,
  type: 'shipping',
};

describe('AddressCardComponent', () => {
  let fixture: ComponentFixture<AddressCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddressCardComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AddressCardComponent);
    fixture.componentRef.setInput('address', mockAddress);
    fixture.detectChanges();
  });

  it('should display the address label', () => {
    expect(fixture.nativeElement.textContent).toContain('Home');
  });

  it('should display the street address', () => {
    expect(fixture.nativeElement.textContent).toContain('123 Main St');
  });

  it('should show default badge when isDefault is true', () => {
    const badge = fixture.nativeElement.querySelector('.address-card__default-badge');
    expect(badge).toBeTruthy();
  });

  it('should emit edit event when edit button is clicked', () => {
    let emitted: Address | undefined;
    fixture.componentInstance.edit.subscribe((addr: Address) => (emitted = addr));

    const editBtn = fixture.nativeElement.querySelector('[data-testid="edit-btn"]');
    editBtn?.click();
    expect(emitted).toEqual(mockAddress);
  });

  it('should emit delete event when delete button is clicked', () => {
    let emitted: string | undefined;
    fixture.componentInstance.delete.subscribe((id: string) => (emitted = id));

    const deleteBtn = fixture.nativeElement.querySelector('[data-testid="delete-btn"]');
    deleteBtn?.click();
    expect(emitted).toBe('addr-1');
  });
});
