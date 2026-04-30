import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileFormComponent } from './profile-form.component';
import { ReactiveFormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { UserProfile } from '../../../core/models/user.model';

const mockProfile: UserProfile = {
  id: 'user-1',
  firstName: 'John',
  lastName: 'Doe',
  email: 'john.doe@example.com',
  phone: '+1234567890',
  createdAt: '2024-01-01T00:00:00Z',
};

describe('ProfileFormComponent', () => {
  let fixture: ComponentFixture<ProfileFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileFormComponent, ReactiveFormsModule, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileFormComponent);
    fixture.componentRef.setInput('profile', mockProfile);
    fixture.detectChanges();
  });

  it('should pre-fill form with profile data', () => {
    const form = fixture.componentInstance.form;
    expect(form.get('firstName')?.value).toBe('John');
    expect(form.get('lastName')?.value).toBe('Doe');
  });

  it('should display email as read-only', () => {
    const emailInput = fixture.nativeElement.querySelector('input[formControlName="email"]');
    expect(emailInput.readOnly).toBeTrue();
  });

  it('should be valid with populated profile data', () => {
    expect(fixture.componentInstance.form.valid).toBeTrue();
  });

  it('should be invalid when firstName is cleared', () => {
    fixture.componentInstance.form.get('firstName')?.setValue('');
    expect(fixture.componentInstance.form.invalid).toBeTrue();
  });

  it('should emit formSubmit with updated values on save', () => {
    fixture.componentInstance.form.get('firstName')?.setValue('Jane');

    let emitted: unknown;
    fixture.componentInstance.formSubmit.subscribe((val) => (emitted = val));

    const submitBtn = fixture.nativeElement.querySelector('[data-testid="save-profile-btn"]');
    submitBtn?.click();
    fixture.detectChanges();

    expect((emitted as { firstName: string }).firstName).toBe('Jane');
  });
});
