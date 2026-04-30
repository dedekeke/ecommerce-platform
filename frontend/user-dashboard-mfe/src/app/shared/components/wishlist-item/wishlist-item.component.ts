import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { WishlistItem } from '../../../core/models/wishlist.model';

@Component({
  selector: 'app-wishlist-item',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule],
  template: `
    <mat-card class="wishlist-item" [class.wishlist-item--out-of-stock]="!item().inStock">
      <div class="wishlist-item__image-wrap">
        @if (item().imageUrl) {
          <img
            [src]="item().imageUrl"
            [alt]="item().productName"
            class="wishlist-item__image"
            loading="lazy"
          />
        } @else {
          <div class="wishlist-item__image-placeholder" aria-hidden="true">
            <mat-icon>image_not_supported</mat-icon>
          </div>
        }
      </div>

      <mat-card-content>
        <h3 class="wishlist-item__name">{{ item().productName }}</h3>
        <p class="wishlist-item__price">{{ item().price | currency: item().currency }}</p>
        <span
          class="wishlist-item__stock"
          [class.wishlist-item__stock--in]="item().inStock"
          [class.wishlist-item__stock--out]="!item().inStock"
        >
          {{ item().inStock ? 'In Stock' : 'Out of Stock' }}
        </span>
      </mat-card-content>

      <mat-card-actions>
        <button
          mat-flat-button
          color="primary"
          data-testid="add-to-cart-btn"
          [disabled]="!item().inStock"
          (click)="addToCart.emit(item())"
          [attr.aria-label]="'Add ' + item().productName + ' to cart'"
        >
          Add to Cart
        </button>
        <button
          mat-icon-button
          color="warn"
          data-testid="remove-btn"
          (click)="remove.emit(item().id)"
          [attr.aria-label]="'Remove ' + item().productName + ' from wishlist'"
        >
          <mat-icon>favorite</mat-icon>
        </button>
      </mat-card-actions>
    </mat-card>
  `,
  styleUrl: './wishlist-item.component.scss',
})
export class WishlistItemComponent {
  readonly item = input.required<WishlistItem>();
  readonly remove = output<string>();
  readonly addToCart = output<WishlistItem>();
}
