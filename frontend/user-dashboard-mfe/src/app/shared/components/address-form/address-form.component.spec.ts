import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AddressFormComponent } from './address-form.component';
import { ReactiveFormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('AddressFormComponent', () => {
  let fixture: ComponentFixture<AddressFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddressFormComponent, ReactiveFormsModule, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AddressFormComponent);
    fixture.detectChanges();
  });

  it('should render the form with required fields', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[formControlName="firstName"]')).toBeTruthy();
    expect(compiled.querySelector('[formControlName="street"]')).toBeTruthy();
    expect(compiled.querySelector('[formControlName="city"]')).toBeTruthy();
    expect(compiled.querySelector('[formControlName="postalCode"]')).toBeTruthy();
  });

  it('should be invalid when required fields are empty', () => {
    const form = fixture.componentInstance.form;
    expect(form.valid).toBeFalse();
  });

  it('should be valid when all required fields are filled', () => {
    const form = fixture.componentInstance.form;
    form.patchValue({
      label: 'Home',
      firstName: 'John',
      lastName: 'Doe',
      street: '123 Main St',
      city: 'New York',
      state: 'NY',
      postalCode: '10001',
      country: 'US',
      type: 'shipping',
    });
    expect(form.valid).toBeTrue();
  });

  it('should emit formSubmit with valid form data on save', () => {
    const form = fixture.componentInstance.form;
    form.patchValue({
      label: 'Home',
      firstName: 'John',
      lastName: 'Doe',
      street: '123 Main St',
      city: 'New York',
      state: 'NY',
      postalCode: '10001',
      country: 'US',
      type: 'shipping',
    });

    let emitted: unknown;
    fixture.componentInstance.formSubmit.subscribe((val) => (emitted = val));

    const submitBtn = fixture.nativeElement.querySelector('[data-testid="submit-btn"]');
    submitBtn?.click();
    fixture.detectChanges();

    expect(emitted).toBeTruthy();
  });

  it('should not emit formSubmit when form is invalid', () => {
    let emitted = false;
    fixture.componentInstance.formSubmit.subscribe(() => (emitted = true));

    const submitBtn = fixture.nativeElement.querySelector('[data-testid="submit-btn"]');
    submitBtn?.click();
    fixture.detectChanges();

    expect(emitted).toBeFalse();
  });
});
