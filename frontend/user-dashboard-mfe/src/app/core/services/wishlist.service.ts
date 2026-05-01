import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { WishlistItem } from '../models/wishlist.model';
import { ApiClientService } from './api-client.service';

const STORAGE_KEY = 'user-wishlist-storage';

/**
 * Backend toggle for the wishlist service.
 *
 * <p>When {@code VITE_WISHLIST_BACKEND_ENABLED=true} the service uses the
 * user-service HTTP API; otherwise it keeps the original localStorage-only
 * behaviour. Default is OFF — the backend ships first and the flag is flipped
 * to {@code true} after a smoke test.</p>
 *
 * <p>The flag is read defensively: if {@code import.meta.env} is unavailable
 * (e.g. running under Karma without a Vite-style env shim) we fall back to
 * disabled. Tests inject the {@link WishlistService} and call
 * {@link WishlistService.setBackendEnabled} to flip the flag explicitly.</p>
 */
function isBackendEnabledFromEnv(): boolean {
  try {
    const meta = (import.meta as unknown) as { env?: Record<string, string | undefined> };
    return meta?.env?.['VITE_WISHLIST_BACKEND_ENABLED'] === 'true';
  } catch {
    return false;
  }
}

export interface WishlistBackendItem {
  id: number;
  productId: string;
  addedAt: string;
}

@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly api = inject(ApiClientService);
  private readonly items$ = new BehaviorSubject<WishlistItem[]>(this.loadFromStorage());

  /** Resolved at construction time; tests override via {@link setBackendEnabled}. */
  private backendEnabled = isBackendEnabledFromEnv();

  /** Current authenticated user id; required when {@code backendEnabled=true}. */
  private currentUserId: string | null = null;

  /** Test/runtime override for the backend flag. */
  setBackendEnabled(enabled: boolean): void {
    this.backendEnabled = enabled;
  }

  setCurrentUserId(userId: string | null): void {
    this.currentUserId = userId;
  }

  isBackendEnabled(): boolean {
    return this.backendEnabled;
  }

  getItems(): Observable<WishlistItem[]> {
    return this.items$.asObservable();
  }

  /**
   * Loads the wishlist from the backend and pushes the result through
   * {@link #items$}. Subscribers receive the new value via {@link getItems()}.
   */
  loadFromBackend(userId: string): Observable<WishlistBackendItem[]> {
    return this.api.get<WishlistBackendItem[]>(`/wishlist/${userId}`).pipe(
      tap((rows) => {
        const mapped: WishlistItem[] = rows.map((r) => ({
          id: String(r.id),
          productId: r.productId,
          productName: '',
          price: 0,
          currency: 'USD',
          inStock: true,
          addedAt: r.addedAt,
        }));
        this.items$.next(mapped);
      }),
    );
  }

  /**
   * Add a product to the wishlist.
   *
   * <p>Behaviour matrix:</p>
   * <ul>
   *   <li><b>backend off (default)</b>: append locally + persist to localStorage.</li>
   *   <li><b>backend on</b>: fire HTTP POST /api/wishlist/{userId}/items and
   *       update {@link #items$} on success. The HTTP call is fire-and-forget
   *       from the caller's perspective so the existing {@code void} return
   *       contract is preserved; failures are logged.</li>
   * </ul>
   */
  addItem(item: Omit<WishlistItem, 'id' | 'addedAt'>): void {
    if (this.backendEnabled && this.currentUserId) {
      this.api
        .post<WishlistBackendItem>(`/wishlist/${this.currentUserId}/items`, {
          productId: item.productId,
        })
        .subscribe({
          next: (row) => {
            const current = this.items$.getValue();
            if (!current.some((i) => i.productId === item.productId)) {
              const newItem: WishlistItem = {
                ...item,
                id: String(row.id),
                addedAt: row.addedAt,
              };
              this.items$.next([...current, newItem]);
            }
          },
          error: (err) => {
            console.error('Failed to add wishlist item via backend', err);
          },
        });
      return;
    }

    // localStorage fallback
    const current = this.items$.getValue();
    if (current.some((i) => i.productId === item.productId)) {
      return;
    }
    const newItem: WishlistItem = {
      ...item,
      id: crypto.randomUUID(),
      addedAt: new Date().toISOString(),
    };
    const updated = [...current, newItem];
    this.persist(updated);
    this.items$.next(updated);
  }

  /**
   * Remove a wishlist entry by its local id. Backend mode also issues
   * DELETE /api/wishlist/{userId}/items/{productId} when the productId
   * is resolvable from the in-memory items.
   */
  removeItem(itemId: string): void {
    const current = this.items$.getValue();
    const target = current.find((i) => i.id === itemId);

    if (this.backendEnabled && this.currentUserId && target) {
      this.api
        .delete<void>(`/wishlist/${this.currentUserId}/items/${target.productId}`)
        .subscribe({
          next: () => {
            this.items$.next(current.filter((i) => i.id !== itemId));
          },
          error: (err) => {
            console.error('Failed to remove wishlist item via backend', err);
          },
        });
      return;
    }

    const updated = current.filter((i) => i.id !== itemId);
    this.persist(updated);
    this.items$.next(updated);
  }

  isInWishlist(productId: string): boolean {
    return this.items$.getValue().some((i) => i.productId === productId);
  }

  private loadFromStorage(): WishlistItem[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as WishlistItem[]) : [];
    } catch {
      return [];
    }
  }

  private persist(items: WishlistItem[]): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch {
      // Silently fail if storage is unavailable
    }
  }
}
