import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { WishlistItem } from '../models/wishlist.model';

const STORAGE_KEY = 'user-wishlist-storage';

@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly items$ = new BehaviorSubject<WishlistItem[]>(this.loadFromStorage());

  getItems(): Observable<WishlistItem[]> {
    return this.items$.asObservable();
  }

  addItem(item: Omit<WishlistItem, 'id' | 'addedAt'>): void {
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

  removeItem(itemId: string): void {
    const updated = this.items$.getValue().filter((i) => i.id !== itemId);
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
