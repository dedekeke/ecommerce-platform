import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Address } from '../../../core/models/user.model';

@Component({
  selector: 'app-address-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule],
  template: `
    <mat-card class="address-card">
      <mat-card-content>
        <div class="address-card__header">
          <div class="address-card__labels">
            <span class="address-card__label">{{ address().label }}</span>
            @if (address().isDefault) {
              <span class="address-card__default-badge">Default</span>
            }
          </div>
          <div class="address-card__type-badge">{{ address().type }}</div>
        </div>

        <address class="address-card__address">
          <span>{{ address().firstName }} {{ address().lastName }}</span>
          <span>{{ address().street }}</span>
          <span>{{ address().city }}, {{ address().state }} {{ address().postalCode }}</span>
          <span>{{ address().country }}</span>
        </address>
      </mat-card-content>

      <mat-card-actions>
        <button
          mat-icon-button
          data-testid="edit-btn"
          (click)="edit.emit(address())"
          [attr.aria-label]="'Edit ' + address().label + ' address'"
        >
          <mat-icon>edit</mat-icon>
        </button>
        <button
          mat-icon-button
          color="warn"
          data-testid="delete-btn"
          (click)="delete.emit(address().id)"
          [attr.aria-label]="'Delete ' + address().label + ' address'"
        >
          <mat-icon>delete</mat-icon>
        </button>
      </mat-card-actions>
    </mat-card>
  `,
  styleUrl: './address-card.component.scss',
})
export class AddressCardComponent {
  readonly address = input.required<Address>();
  readonly edit = output<Address>();
  readonly delete = output<string>();
}
