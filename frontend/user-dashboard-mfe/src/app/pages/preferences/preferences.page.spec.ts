import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { PreferencesPage } from './preferences.page';
import { PreferencesService } from '../../core/services/preferences.service';
import { ToastService } from '../../core/services/toast.service';
import { DEFAULT_PREFERENCES, UserPreferences } from '../../core/models/preferences.model';

describe('PreferencesPage', () => {
  let fixture: ComponentFixture<PreferencesPage>;
  let preferencesServiceSpy: jasmine.SpyObj<PreferencesService>;
  let toastServiceSpy: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    preferencesServiceSpy = jasmine.createSpyObj('PreferencesService', ['getPreferences', 'save']);
    preferencesServiceSpy.getPreferences.and.returnValue(of(DEFAULT_PREFERENCES));
    toastServiceSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [PreferencesPage, NoopAnimationsModule],
      providers: [
        { provide: PreferencesService, useValue: preferencesServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PreferencesPage);
    fixture.detectChanges();
  });

  it('should display the page title', () => {
    expect(fixture.nativeElement.textContent).toContain('Preferences');
  });

  it('should render the preferences form pre-filled from the service', () => {
    const form = fixture.nativeElement.querySelector('app-preferences-form');
    expect(form).toBeTruthy();
  });

  it('should save updated preferences and show a success toast on submit', () => {
    const updated: UserPreferences = {
      ...DEFAULT_PREFERENCES,
      display: { theme: 'dark', language: 'en', currency: 'USD' },
    };

    fixture.componentInstance.onSave(updated);

    expect(preferencesServiceSpy.save).toHaveBeenCalledWith(updated);
    expect(toastServiceSpy.success).toHaveBeenCalledWith('Preferences updated');
  });
});
