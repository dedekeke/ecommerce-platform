import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { DEFAULT_PREFERENCES, UserPreferences } from '../models/preferences.model';

const STORAGE_KEY = 'user-preferences-storage';

/**
 * user-service currently exposes no preferences/notification-settings endpoint
 * (only GET/PUT /api/users/me for name/phone — see UserController). Until a
 * backend endpoint ships, preferences are persisted to localStorage behind
 * this typed abstraction so callers can swap the storage for an HTTP call
 * without touching the rest of the app.
 */
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly preferences$ = new BehaviorSubject<UserPreferences>(this.loadFromStorage());

  getPreferences(): Observable<UserPreferences> {
    return this.preferences$.asObservable();
  }

  getSnapshot(): UserPreferences {
    return this.preferences$.getValue();
  }

  save(preferences: UserPreferences): void {
    this.persist(preferences);
    this.preferences$.next(preferences);
  }

  resetToDefaults(): void {
    this.save(DEFAULT_PREFERENCES);
  }

  private loadFromStorage(): UserPreferences {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return DEFAULT_PREFERENCES;
      }
      const parsed = JSON.parse(raw) as Partial<UserPreferences>;
      return {
        notifications: { ...DEFAULT_PREFERENCES.notifications, ...parsed.notifications },
        display: { ...DEFAULT_PREFERENCES.display, ...parsed.display },
        emailSubscriptions: { ...DEFAULT_PREFERENCES.emailSubscriptions, ...parsed.emailSubscriptions },
      };
    } catch {
      return DEFAULT_PREFERENCES;
    }
  }

  private persist(preferences: UserPreferences): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences));
    } catch {
      // Silently fail if storage is unavailable
    }
  }
}
