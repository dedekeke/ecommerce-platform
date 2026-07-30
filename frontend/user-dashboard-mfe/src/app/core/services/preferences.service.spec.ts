import { TestBed } from '@angular/core/testing';
import { PreferencesService } from './preferences.service';
import { DEFAULT_PREFERENCES, UserPreferences } from '../models/preferences.model';

const STORAGE_KEY = 'user-preferences-storage';

describe('PreferencesService', () => {
  let service: PreferencesService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [PreferencesService] });
    service = TestBed.inject(PreferencesService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should emit the default preferences when nothing is stored', (done) => {
    service.getPreferences().subscribe((prefs) => {
      expect(prefs).toEqual(DEFAULT_PREFERENCES);
      done();
    });
  });

  it('should persist saved preferences to localStorage', () => {
    const updated: UserPreferences = {
      ...DEFAULT_PREFERENCES,
      display: { theme: 'dark', language: 'fr', currency: 'EUR' },
    };

    service.save(updated);

    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
    expect(stored.display).toEqual({ theme: 'dark', language: 'fr', currency: 'EUR' });
  });

  it('should emit the newly saved preferences to subscribers', () => {
    const updated: UserPreferences = {
      ...DEFAULT_PREFERENCES,
      emailSubscriptions: { newsletter: false, productAnnouncements: true, surveys: true },
    };

    service.save(updated);

    let received: UserPreferences | undefined;
    service.getPreferences().subscribe((prefs) => (received = prefs));
    expect(received).toEqual(updated);
  });

  it('should load previously persisted preferences on construction', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ ...DEFAULT_PREFERENCES, display: { theme: 'light', language: 'de', currency: 'GBP' } })
    );

    const freshService = new PreferencesService();
    expect(freshService.getSnapshot().display).toEqual({ theme: 'light', language: 'de', currency: 'GBP' });
  });

  it('should fall back to defaults when localStorage contains malformed JSON', () => {
    localStorage.setItem(STORAGE_KEY, 'not-json');

    expect(() => new PreferencesService()).not.toThrow();
    expect(new PreferencesService().getSnapshot()).toEqual(DEFAULT_PREFERENCES);
  });

  it('should reset to default preferences', () => {
    service.save({ ...DEFAULT_PREFERENCES, display: { theme: 'dark', language: 'es', currency: 'EUR' } });

    service.resetToDefaults();

    expect(service.getSnapshot()).toEqual(DEFAULT_PREFERENCES);
  });
});
