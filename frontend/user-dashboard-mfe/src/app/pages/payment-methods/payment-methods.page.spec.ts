import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { NEVER, of, throwError } from 'rxjs';
import { PaymentMethodsPage } from './payment-methods.page';
import { PaymentMethodService } from '../../core/services/payment-method.service';
import { StripeLoaderService, StripeInstance } from '../../core/services/stripe-loader.service';
import { SavedPaymentMethod } from '../../core/models/payment-method.model';

const mockMethods: SavedPaymentMethod[] = [
  {
    id: 1,
    userId: 'me',
    provider: 'stripe',
    providerId: 'pm_123',
    last4: '4242',
    brand: 'visa',
    expMonth: 12,
    expYear: 2030,
    isDefault: true,
    createdAt: '2026-01-01T00:00:00Z',
  },
];

describe('PaymentMethodsPage', () => {
  let fixture: ComponentFixture<PaymentMethodsPage>;
  let paymentMethodServiceSpy: jasmine.SpyObj<PaymentMethodService>;
  let stripeLoaderSpy: jasmine.SpyObj<StripeLoaderService>;
  let fakeMount: jasmine.Spy;
  let fakeCreate: jasmine.Spy;
  let fakeConfirmSetup: jasmine.Spy;
  let fakeElements: jasmine.Spy;
  let fakeStripe: StripeInstance;

  function setup(): void {
    fixture = TestBed.createComponent(PaymentMethodsPage);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    paymentMethodServiceSpy = jasmine.createSpyObj('PaymentMethodService', [
      'list',
      'createSetupIntent',
      'confirmSetupIntent',
      'delete',
      'setDefault',
    ]);
    paymentMethodServiceSpy.list.and.returnValue(of(mockMethods));

    fakeMount = jasmine.createSpy('mount');
    fakeCreate = jasmine.createSpy('create').and.returnValue({ mount: fakeMount, unmount: jasmine.createSpy() });
    fakeElements = jasmine.createSpy('elements').and.returnValue({ create: fakeCreate });
    fakeConfirmSetup = jasmine.createSpy('confirmSetup');
    fakeStripe = { elements: fakeElements, confirmSetup: fakeConfirmSetup } as unknown as StripeInstance;

    stripeLoaderSpy = jasmine.createSpyObj('StripeLoaderService', ['load']);
    stripeLoaderSpy.load.and.returnValue(Promise.resolve(fakeStripe));

    await TestBed.configureTestingModule({
      imports: [PaymentMethodsPage, NoopAnimationsModule],
      providers: [
        { provide: PaymentMethodService, useValue: paymentMethodServiceSpy },
        { provide: StripeLoaderService, useValue: stripeLoaderSpy },
      ],
    }).compileComponents();
  });

  describe('loading, error and empty states', () => {
    it('should show the loading skeleton before the list resolves', () => {
      // Uses an observable that never emits so the component stays in the loading state —
      // of(mockMethods) resolves synchronously on subscribe, which would race past "loading".
      paymentMethodServiceSpy.list.and.returnValue(NEVER);
      fixture = TestBed.createComponent(PaymentMethodsPage);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="loading-skeleton"]')).toBeTruthy();
    });

    it('should render a card per saved method once loaded', () => {
      setup();
      const cards = fixture.nativeElement.querySelectorAll('app-payment-method-card');
      expect(cards.length).toBe(1);
    });

    it('should show an error state with a retry button when loading fails', () => {
      paymentMethodServiceSpy.list.and.returnValue(throwError(() => new Error('boom')));
      setup();

      const errorState = fixture.nativeElement.querySelector('[data-testid="error-state"]');
      expect(errorState).toBeTruthy();
      expect(fixture.nativeElement.querySelector('[data-testid="loading-skeleton"]')).toBeNull();
    });

    it('should reload the list when retry is clicked after an error', () => {
      paymentMethodServiceSpy.list.and.returnValue(throwError(() => new Error('boom')));
      setup();

      paymentMethodServiceSpy.list.and.returnValue(of(mockMethods));
      fixture.nativeElement.querySelector('[data-testid="retry-btn"]')?.click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelectorAll('app-payment-method-card').length).toBe(1);
    });

    it('should show an empty state when there are no saved methods', () => {
      paymentMethodServiceSpy.list.and.returnValue(of([]));
      setup();

      expect(fixture.nativeElement.querySelector('[data-testid="empty-state"]')).toBeTruthy();
    });
  });

  describe('deleting a saved method', () => {
    beforeEach(() => setup());

    it('should call delete and reload the list on success', () => {
      paymentMethodServiceSpy.delete.and.returnValue(of(undefined));
      paymentMethodServiceSpy.list.and.returnValue(of([]));

      fixture.componentInstance.onDelete(1);
      fixture.detectChanges();

      expect(paymentMethodServiceSpy.delete).toHaveBeenCalledWith(1);
      expect(paymentMethodServiceSpy.list).toHaveBeenCalledTimes(2);
      expect(fixture.nativeElement.querySelector('[data-testid="empty-state"]')).toBeTruthy();
    });

    it('should clear the deleting state if the delete call fails', () => {
      paymentMethodServiceSpy.delete.and.returnValue(throwError(() => new Error('boom')));

      fixture.componentInstance.onDelete(1);
      fixture.detectChanges();

      expect(fixture.componentInstance.deletingId()).toBeNull();
    });
  });

  describe('setting a default method', () => {
    beforeEach(() => setup());

    it('should call setDefault and reload the list on success', () => {
      paymentMethodServiceSpy.setDefault.and.returnValue(of(mockMethods[0]));

      fixture.componentInstance.onSetDefault(1);

      expect(paymentMethodServiceSpy.setDefault).toHaveBeenCalledWith(1);
      expect(paymentMethodServiceSpy.list).toHaveBeenCalledTimes(2);
    });

    it('should not reload the list or throw when setDefault fails', () => {
      paymentMethodServiceSpy.setDefault.and.returnValue(throwError(() => new Error('boom')));

      expect(() => fixture.componentInstance.onSetDefault(1)).not.toThrow();
      expect(paymentMethodServiceSpy.list).toHaveBeenCalledTimes(1);
    });
  });

  describe('adding a card', () => {
    beforeEach(() => setup());

    it('should request a setup intent and load Stripe when Add Card is clicked', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(
        of({ setupIntentId: 'seti_1', clientSecret: 'secret_1' })
      );

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="add-card-panel"]')).toBeTruthy();

      flushMicrotasks();
      fixture.detectChanges();

      expect(stripeLoaderSpy.load).toHaveBeenCalled();
      expect(fakeElements).toHaveBeenCalledWith({ clientSecret: 'secret_1' });
      expect(fakeCreate).toHaveBeenCalledWith('payment');
      expect(fakeMount).toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')).toBeTruthy();
    }));

    it('should show a configuration error when Stripe fails to load (no publishable key)', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(
        of({ setupIntentId: 'seti_1', clientSecret: 'secret_1' })
      );
      stripeLoaderSpy.load.and.returnValue(Promise.resolve(null));

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      const error = fixture.nativeElement.querySelector('[data-testid="add-card-error"]');
      expect(error?.textContent).toContain('not configured');
      expect(fixture.nativeElement.querySelector('[data-testid="add-card-panel"]')).toBeTruthy();
      expect(fixture.nativeElement.querySelector('[data-testid="close-add-card-btn"]')).toBeTruthy();
      expect(fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')).toBeNull();
    }));

    it('should show an error when the setup intent request fails', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(throwError(() => new Error('boom')));

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      const error = fixture.nativeElement.querySelector('[data-testid="add-card-error"]');
      expect(error).toBeTruthy();
      expect(fixture.nativeElement.querySelector('[data-testid="close-add-card-btn"]')).toBeTruthy();
    }));

    it('should close the panel and allow retrying after a blocked add-card error', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(throwError(() => new Error('boom')));

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="close-add-card-btn"]')?.click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="add-card-panel"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="add-card-btn"]').disabled).toBeFalse();
    }));

    it('should confirm the setup intent and reload the list on the happy path', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(
        of({ setupIntentId: 'seti_1', clientSecret: 'secret_1' })
      );
      fakeConfirmSetup.and.returnValue(
        Promise.resolve({ setupIntent: { id: 'seti_1', status: 'succeeded' } })
      );
      paymentMethodServiceSpy.confirmSetupIntent.and.returnValue(of(mockMethods[0]));

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      expect(fakeConfirmSetup).toHaveBeenCalledWith({ elements: jasmine.anything(), redirect: 'if_required' });
      expect(paymentMethodServiceSpy.confirmSetupIntent).toHaveBeenCalledWith('seti_1');
      expect(paymentMethodServiceSpy.list).toHaveBeenCalledTimes(2);
      expect(fixture.nativeElement.querySelector('[data-testid="add-card-panel"]')).toBeNull();
    }));

    it('should surface an error message when Stripe confirmSetup returns an error', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(
        of({ setupIntentId: 'seti_1', clientSecret: 'secret_1' })
      );
      fakeConfirmSetup.and.returnValue(
        Promise.resolve({ error: { message: 'Your card was declined.' } })
      );

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      const error = fixture.nativeElement.querySelector('[data-testid="add-card-error"]');
      expect(error?.textContent).toContain('Your card was declined.');
      expect(paymentMethodServiceSpy.confirmSetupIntent).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')).toBeTruthy();
    }));

    it('should surface an error message when confirmSetupIntent fails on the backend', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(
        of({ setupIntentId: 'seti_1', clientSecret: 'secret_1' })
      );
      fakeConfirmSetup.and.returnValue(
        Promise.resolve({ setupIntent: { id: 'seti_1', status: 'succeeded' } })
      );
      paymentMethodServiceSpy.confirmSetupIntent.and.returnValue(throwError(() => new Error('boom')));

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      const error = fixture.nativeElement.querySelector('[data-testid="add-card-error"]');
      expect(error).toBeTruthy();
    }));

    it('should close the add-card panel when Cancel is clicked', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(
        of({ setupIntentId: 'seti_1', clientSecret: 'secret_1' })
      );

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="cancel-add-card-btn"]')?.click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="add-card-panel"]')).toBeNull();
    }));

    it('should no-op when confirming a card before Stripe Elements has been initialized', () => {
      expect(() => fixture.componentInstance.onConfirmAddCard()).not.toThrow();
      expect(fakeConfirmSetup).not.toHaveBeenCalled();
    });

    it('should show a generic error when confirmSetup resolves without an error or a setupIntent id', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(
        of({ setupIntentId: 'seti_1', clientSecret: 'secret_1' })
      );
      fakeConfirmSetup.and.returnValue(Promise.resolve({}));

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      const error = fixture.nativeElement.querySelector('[data-testid="add-card-error"]');
      expect(error?.textContent).toContain("try again");
      expect(paymentMethodServiceSpy.confirmSetupIntent).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')).toBeTruthy();
    }));

    it('should show a generic error when the confirmSetup promise itself rejects', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(
        of({ setupIntentId: 'seti_1', clientSecret: 'secret_1' })
      );
      // Return the rejected promise lazily via callFake so it is only constructed once
      // confirmSetup() is invoked — chaining .catch() attaches synchronously right after,
      // avoiding a spurious "Uncaught (in promise)" from an earlier, still-unhandled tick.
      fakeConfirmSetup.and.callFake(() => Promise.reject(new Error('network down')));

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      const error = fixture.nativeElement.querySelector('[data-testid="add-card-error"]');
      expect(error).toBeTruthy();
      expect(fixture.nativeElement.querySelector('[data-testid="confirm-add-card-btn"]')).toBeTruthy();
    }));

    it('should show a blocked error state when the Stripe loader promise rejects', fakeAsync(() => {
      paymentMethodServiceSpy.createSetupIntent.and.returnValue(
        of({ setupIntentId: 'seti_1', clientSecret: 'secret_1' })
      );
      stripeLoaderSpy.load.and.callFake(() => Promise.reject(new Error('network down')));

      fixture.nativeElement.querySelector('[data-testid="add-card-btn"]')?.click();
      fixture.detectChanges();
      flushMicrotasks();
      fixture.detectChanges();

      const error = fixture.nativeElement.querySelector('[data-testid="add-card-error"]');
      expect(error).toBeTruthy();
      expect(fixture.nativeElement.querySelector('[data-testid="close-add-card-btn"]')).toBeTruthy();
    }));
  });
});
