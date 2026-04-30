import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { WishlistService } from '../../core/services/wishlist.service';
import { WishlistItem } from '../../core/models/wishlist.model';
import { WishlistItemComponent } from '../../shared/components/wishlist-item/wishlist-item.component';

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

  readonly items = signal<WishlistItem[]>([]);

  ngOnInit(): void {
    this.wishlistService.getItems().subscribe((list) => this.items.set(list));
  }

  onRemoveItem(itemId: string): void {
    this.wishlistService.removeItem(itemId);
  }

  onAddToCart(item: WishlistItem): void {
    // Dispatch to shell's cart event bus when integrated
    window.dispatchEvent(
      new CustomEvent('mfe:addToCart', {
        detail: { productId: item.productId, quantity: 1 },
        bubbles: true,
      })
    );
  }
}
