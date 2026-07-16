import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { PaymentMethodCardComponent } from './payment-method-card.component';
import { SavedPaymentMethod } from '../../../core/models/payment-method.model';

const mockMethod: SavedPaymentMethod = {
  id: 1,
  userId: 'me',
  provider: 'stripe',
  providerId: 'pm_123',
  last4: '4242',
  brand: 'visa',
  expMonth: 9,
  expYear: 2031,
  isDefault: false,
  createdAt: '2026-01-01T00:00:00Z',
};

describe('PaymentMethodCardComponent', () => {
  let fixture: ComponentFixture<PaymentMethodCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentMethodCardComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentMethodCardComponent);
    fixture.componentRef.setInput('method', mockMethod);
    fixture.detectChanges();
  });

  it('should display the masked card number with last4', () => {
    expect(fixture.nativeElement.textContent).toContain('4242');
  });

  it('should display the capitalized brand name', () => {
    expect(fixture.nativeElement.textContent).toContain('Visa');
  });

  it('should display the formatted expiry date', () => {
    expect(fixture.nativeElement.textContent).toContain('09/2031');
  });

  it('should not show the default badge when isDefault is false', () => {
    const badge = fixture.nativeElement.querySelector('.payment-method-card__default-badge');
    expect(badge).toBeNull();
  });

  it('should show the default badge when isDefault is true', () => {
    fixture.componentRef.setInput('method', { ...mockMethod, isDefault: true });
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.payment-method-card__default-badge');
    expect(badge).toBeTruthy();
  });

  it('should show a "Set as default" button when the card is not default', () => {
    const btn = fixture.nativeElement.querySelector('[data-testid="set-default-btn"]');
    expect(btn).toBeTruthy();
  });

  it('should hide the "Set as default" button when the card is already default', () => {
    fixture.componentRef.setInput('method', { ...mockMethod, isDefault: true });
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="set-default-btn"]');
    expect(btn).toBeNull();
  });

  it('should emit setDefault with the method id when "Set as default" is clicked', () => {
    let emitted: number | undefined;
    fixture.componentInstance.setDefault.subscribe((id: number) => (emitted = id));

    fixture.nativeElement.querySelector('[data-testid="set-default-btn"]')?.click();
    expect(emitted).toBe(1);
  });

  it('should show an inline confirmation instead of emitting delete immediately', () => {
    let emitted = false;
    fixture.componentInstance.delete.subscribe(() => (emitted = true));

    fixture.nativeElement.querySelector('[data-testid="delete-btn"]')?.click();
    fixture.detectChanges();

    expect(emitted).toBeFalse();
    expect(fixture.nativeElement.querySelector('[data-testid="confirm-delete-btn"]')).toBeTruthy();
  });

  it('should emit delete with the method id when the delete confirmation is accepted', () => {
    let emitted: number | undefined;
    fixture.componentInstance.delete.subscribe((id: number) => (emitted = id));

    fixture.nativeElement.querySelector('[data-testid="delete-btn"]')?.click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="confirm-delete-btn"]')?.click();

    expect(emitted).toBe(1);
  });

  it('should dismiss the confirmation without emitting delete when cancelled', () => {
    let emitted = false;
    fixture.componentInstance.delete.subscribe(() => (emitted = true));

    fixture.nativeElement.querySelector('[data-testid="delete-btn"]')?.click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="cancel-delete-btn"]')?.click();
    fixture.detectChanges();

    expect(emitted).toBeFalse();
    expect(fixture.nativeElement.querySelector('[data-testid="delete-btn"]')).toBeTruthy();
  });

  it('should disable the confirm-delete button while deleting is true', () => {
    fixture.nativeElement.querySelector('[data-testid="delete-btn"]')?.click();
    fixture.componentRef.setInput('deleting', true);
    fixture.detectChanges();

    const confirmBtn = fixture.nativeElement.querySelector('[data-testid="confirm-delete-btn"]');
    expect(confirmBtn.disabled).toBeTrue();
  });
});
