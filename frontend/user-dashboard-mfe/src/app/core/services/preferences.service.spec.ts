import { TestBed } from '@angular/core/testing';
import { PreferencesService } from './preferences.service';
import { DEFAULT_PREFERENCES, UserPreferences } from '../models/preferences.model';

const STORAGE_KEY = 'user-preferences-storage:anonymous';

describe('PreferencesService', () => {
  let service: PreferencesService;

  beforeEach(() => {
    localStorage.clear();
    delete window.__getAuthUserId;
    TestBed.configureTestingModule({ providers: [PreferencesService] });
    service = TestBed.inject(PreferencesService);
  });

  afterEach(() => {
    localStorage.clear();
    delete window.__getAuthUserId;
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

  describe('per-user namespacing', () => {
    it('should namespace the storage key with the authenticated user id', () => {
      window.__getAuthUserId = () => 'user-42';
      const scopedService = new PreferencesService();

      scopedService.save({ ...DEFAULT_PREFERENCES, display: { theme: 'dark', language: 'fr', currency: 'EUR' } });

      expect(localStorage.getItem('user-preferences-storage:user-42')).toBeTruthy();
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    });

    it('should not leak preferences saved by one user into another user session', () => {
      window.__getAuthUserId = () => 'user-1';
      const userOneService = new PreferencesService();
      userOneService.save({ ...DEFAULT_PREFERENCES, display: { theme: 'dark', language: 'fr', currency: 'EUR' } });

      window.__getAuthUserId = () => 'user-2';
      const userTwoService = new PreferencesService();

      expect(userTwoService.getSnapshot()).toEqual(DEFAULT_PREFERENCES);
    });

    it('should fall back to an anonymous key when unauthenticated', () => {
      window.__getAuthUserId = () => null;
      const anonymousService = new PreferencesService();

      anonymousService.save({ ...DEFAULT_PREFERENCES, display: { theme: 'dark', language: 'de', currency: 'GBP' } });

      expect(localStorage.getItem(STORAGE_KEY)).toBeTruthy();
    });

    it('should fall back to an anonymous key when __getAuthUserId is not installed', () => {
      const anonymousService = new PreferencesService();

      anonymousService.save({ ...DEFAULT_PREFERENCES, display: { theme: 'light', language: 'vi', currency: 'VND' } });

      expect(localStorage.getItem(STORAGE_KEY)).toBeTruthy();
    });
  });
});
