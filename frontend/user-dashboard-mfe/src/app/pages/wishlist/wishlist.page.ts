import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { WishlistService } from '../../core/services/wishlist.service';
import { CartService } from '../../core/services/cart.service';
import { ToastService } from '../../core/services/toast.service';
import { WishlistItem } from '../../core/models/wishlist.model';
import { WishlistItemComponent } from '../../shared/components/wishlist-item/wishlist-item.component';

declare global {
  interface Window {
    __cartBridge?: {
      addItem: (item: {
        productId: string;
        name: string;
        price: number;
        image?: string;
        quantity?: number;
      }) => void;
    };
  }
}

@Component({
  selector: 'app-wishlist-page',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, WishlistItemComponent],
  template: `
    <div class="wishlist-page container">
      <header class="wishlist-page__header">
        <h1 class="wishlist-page__title">Wishlist</h1>
        <span class="wishlist-page__count" [attr.aria-label]="items().length + ' items in wishlist'">
          {{ items().length }} item{{ items().length !== 1 ? 's' : '' }}
        </span>
      </header>

      @if (items().length === 0) {
        <div class="wishlist-page__empty">
          <mat-icon class="wishlist-page__empty-icon" aria-hidden="true">favorite_border</mat-icon>
          <h2>No favorites yet. Start your collection!</h2>
          <a mat-flat-button color="primary" href="/catalog">Browse Products</a>
        </div>
      } @else {
        <div class="wishlist-grid">
          @for (item of items(); track item.id) {
            <app-wishlist-item
              [item]="item"
              (remove)="onRemoveItem($event)"
              (addToCart)="onAddToCart($event)"
            />
          }
        </div>
      }
    </div>
  `,
  styleUrl: './wishlist.page.scss',
})
export class WishlistPage implements OnInit {
  private readonly wishlistService = inject(WishlistService);
  private readonly cartService = inject(CartService);
  private readonly toast = inject(ToastService);

  readonly items = signal<WishlistItem[]>([]);

  ngOnInit(): void {
    this.wishlistService.getItems().subscribe((list) => this.items.set(list));
  }

  onRemoveItem(itemId: string): void {
    this.wishlistService.removeItem(itemId);
  }

  onAddToCart(item: WishlistItem): void {
    this.cartService.addItem({ productId: item.productId, quantity: 1 }).subscribe({
      next: () => {
        this.toast.success('Added to cart');
        // Server cart updated; also write into the shell's local cart store so the
        // header badge / cart page / checkout (which all read cart-storage) reflect it.
        window.__cartBridge?.addItem({
          productId: item.productId,
          name: item.productName,
          price: item.price,
          image: item.imageUrl,
        });
      },
      // httpErrorInterceptor skips toasting 401/403 (MFE-level auth handling), so handle
      // those explicitly here; other statuses are already toasted by the interceptor.
      error: (err: HttpErrorResponse) => {
        if (err.status === 401 || err.status === 403) {
          this.toast.error('Please sign in again to add items to your cart');
        }
      },
    });
  }
}
