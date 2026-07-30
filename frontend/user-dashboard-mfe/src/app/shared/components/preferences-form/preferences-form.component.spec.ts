import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PreferencesFormComponent } from './preferences-form.component';
import { ReactiveFormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { DEFAULT_PREFERENCES, UserPreferences } from '../../../core/models/preferences.model';

describe('PreferencesFormComponent', () => {
  let fixture: ComponentFixture<PreferencesFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PreferencesFormComponent, ReactiveFormsModule, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(PreferencesFormComponent);
    fixture.componentRef.setInput('preferences', DEFAULT_PREFERENCES);
    fixture.detectChanges();
  });

  it('should pre-fill the notification toggles from the given preferences', () => {
    const form = fixture.componentInstance.form;
    expect(form.get(['notifications', 'orderUpdates', 'email'])?.value).toBeTrue();
    expect(form.get(['notifications', 'promotions', 'sms'])?.value).toBeFalse();
  });

  it('should pre-fill display preferences', () => {
    const form = fixture.componentInstance.form;
    expect(form.get(['display', 'theme'])?.value).toBe('system');
    expect(form.get(['display', 'language'])?.value).toBe('en');
    expect(form.get(['display', 'currency'])?.value).toBe('USD');
  });

  it('should pre-fill email subscription toggles', () => {
    const form = fixture.componentInstance.form;
    expect(form.get(['emailSubscriptions', 'newsletter'])?.value).toBeTrue();
    expect(form.get(['emailSubscriptions', 'productAnnouncements'])?.value).toBeFalse();
  });

  it('should render a toggle for every notification category and channel', () => {
    const toggles = fixture.nativeElement.querySelectorAll('mat-slide-toggle');
    // 4 categories x 3 channels + 3 email subscription toggles
    expect(toggles.length).toBe(4 * 3 + 3);
  });

  it('should emit formSubmit with the current form value on submit', async () => {
    fixture.componentInstance.form.get(['display', 'theme'])?.setValue('dark');

    let emitted: UserPreferences | undefined;
    fixture.componentInstance.formSubmit.subscribe((val) => (emitted = val));

    fixture.componentInstance.onSubmit();
    await fixture.whenStable();

    expect(emitted?.display.theme).toBe('dark');
  });

  it('should reflect a toggle flip in the underlying form control', () => {
    const control = fixture.componentInstance.form.get(['notifications', 'security', 'sms']);
    expect(control?.value).toBeTrue();

    control?.setValue(false);

    expect(control?.value).toBeFalse();
  });
});
