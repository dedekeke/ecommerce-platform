import { Component, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { SavedPaymentMethod } from '../../../core/models/payment-method.model';

@Component({
  selector: 'app-payment-method-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule],
  template: `
    <mat-card class="payment-method-card">
      <mat-card-content>
        <div class="payment-method-card__header">
          <div class="payment-method-card__brand">
            <mat-icon aria-hidden="true">credit_card</mat-icon>
            <span>{{ brandLabel() }}</span>
          </div>
          @if (method().isDefault) {
            <span class="payment-method-card__default-badge">Default</span>
          }
        </div>

        <p
          class="payment-method-card__number"
          [attr.aria-label]="brandLabel() + ' card ending in ' + method().last4"
        >
          •••• •••• •••• {{ method().last4 }}
        </p>
        <p class="payment-method-card__expiry">Expires {{ expiryLabel() }}</p>
      </mat-card-content>

      <mat-card-actions>
        @if (!confirmingDelete()) {
          @if (!method().isDefault) {
            <button
              mat-button
              data-testid="set-default-btn"
              (click)="setDefault.emit(method().id)"
              [attr.aria-label]="'Set ' + brandLabel() + ' card ending in ' + method().last4 + ' as default'"
            >
              Set as default
            </button>
          }
          <button
            mat-icon-button
            color="warn"
            data-testid="delete-btn"
            (click)="confirmingDelete.set(true)"
            [attr.aria-label]="'Remove ' + brandLabel() + ' card ending in ' + method().last4"
          >
            <mat-icon>delete</mat-icon>
          </button>
        } @else {
          <span class="payment-method-card__confirm-label">Remove this card?</span>
          <button
            mat-button
            color="warn"
            data-testid="confirm-delete-btn"
            (click)="onConfirmDelete()"
            [disabled]="deleting()"
            aria-label="Confirm card removal"
          >
            {{ deleting() ? 'Removing…' : 'Yes, remove' }}
          </button>
          <button
            mat-button
            data-testid="cancel-delete-btn"
            (click)="confirmingDelete.set(false)"
            [disabled]="deleting()"
            aria-label="Cancel card removal"
          >
            Cancel
          </button>
        }
      </mat-card-actions>
    </mat-card>
  `,
  styleUrl: './payment-method-card.component.scss',
})
export class PaymentMethodCardComponent {
  readonly method = input.required<SavedPaymentMethod>();
  readonly deleting = input<boolean>(false);
  readonly delete = output<number>();
  readonly setDefault = output<number>();

  readonly confirmingDelete = signal(false);

  brandLabel(): string {
    const brand = this.method().brand;
    return brand ? brand.charAt(0).toUpperCase() + brand.slice(1) : 'Card';
  }

  expiryLabel(): string {
    const month = this.method().expMonth;
    const paddedMonth = month < 10 ? `0${month}` : `${month}`;
    return `${paddedMonth}/${this.method().expYear}`;
  }

  onConfirmDelete(): void {
    this.delete.emit(this.method().id);
  }
}
